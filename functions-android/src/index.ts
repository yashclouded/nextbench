import { createHash } from "node:crypto";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging, MulticastMessage } from "firebase-admin/messaging";
import { initializeApp } from "firebase-admin/app";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { messagePreview, stringList, stringValue } from "./message";

initializeApp();

const db = getFirestore();
const messaging = getMessaging();
const MAX_TOKEN_LENGTH = 4096;
const MAX_TOKENS_PER_USER = 10;
const MAX_RECIPIENTS = 50;

export const registerAndroidPushToken = onCall(async (request) => {
  const uid = requireAuth(request.auth?.uid);
  const token = validateToken(request.data?.token);

  const userRef = db.collection("users").doc(uid);
  const registryRef = db.collection("androidPushTokens").doc(tokenId(token));
  await db.runTransaction(async (transaction) => {
    const registrySnap = await transaction.get(registryRef);
    const previousOwnerId = stringValue(registrySnap.data()?.userId);
    const previousOwnerRef = previousOwnerId && previousOwnerId !== uid
      ? db.collection("users").doc(previousOwnerId)
      : null;
    const [userSnap, previousOwnerSnap] = await Promise.all([
      transaction.get(userRef),
      previousOwnerRef ? transaction.get(previousOwnerRef) : Promise.resolve(null),
    ]);
    const nextTokens = [
      ...stringList(userSnap.data()?.androidFcmTokens).filter((existing) => existing !== token),
      token,
    ].slice(-MAX_TOKENS_PER_USER);

    transaction.set(userRef, { androidFcmTokens: nextTokens }, { merge: true });
    if (previousOwnerRef && previousOwnerSnap?.exists) {
      transaction.set(
        previousOwnerRef,
        { androidFcmTokens: FieldValue.arrayRemove(token) },
        { merge: true },
      );
    }
    transaction.set(registryRef, {
      userId: uid,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return { success: true };
});

export const removeAndroidPushToken = onCall(async (request) => {
  const uid = requireAuth(request.auth?.uid);
  const token = validateToken(request.data?.token);

  const userRef = db.collection("users").doc(uid);
  const registryRef = db.collection("androidPushTokens").doc(tokenId(token));
  await db.runTransaction(async (transaction) => {
    const registrySnap = await transaction.get(registryRef);
    transaction.set(
      userRef,
      { androidFcmTokens: FieldValue.arrayRemove(token) },
      { merge: true },
    );
    if (stringValue(registrySnap.data()?.userId) === uid) transaction.delete(registryRef);
  });

  return { success: true };
});

/** Sends Android chat pushes and creates the matching in-app notifications. */
export const notifyAndroidOnNewMessage = onDocumentCreated(
  "chatRooms/{roomId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    const senderId = stringValue(message.senderId);
    const roomId = event.params.roomId;
    const messageId = event.params.messageId;
    if (!senderId || !roomId || !messageId) return;

    const roomSnap = await db.collection("chatRooms").doc(roomId).get();
    if (!roomSnap.exists) return;
    const room = roomSnap.data() ?? {};
    const participants = stringList(room.participants)
      .filter((uid) => uid !== senderId)
      .filter((uid) => !stringList(room.mutedBy).includes(uid))
      .slice(0, MAX_RECIPIENTS);
    if (participants.length === 0) return;

    const senderSnap = await db.collection("users").doc(senderId).get();
    const senderName = stringValue(message.senderName) || stringValue(senderSnap.data()?.name) || "Someone";
    const preview = messagePreview(message);
    const title = `${senderName} sent you a message`;
    const link = `/chat/${roomId}`;

    const recipientDocs = await Promise.all(
      participants.map(async (recipientId) => {
        const notificationId = `chat_${messageId}_${recipientId}`;
        const ref = db.collection("notifications").doc(notificationId);
        await ref.create({
          userId: recipientId,
          type: "new_message",
          title,
          message: preview,
          link,
          read: false,
          createdAt: FieldValue.serverTimestamp(),
        }).catch((error: unknown) => {
          if (!isAlreadyExists(error)) throw error;
        });
        return { recipientId, notificationId };
      }),
    );

    const users = await Promise.all(
      recipientDocs.map(({ recipientId }) => db.collection("users").doc(recipientId).get()),
    );
    const tokenOwners: Array<{ uid: string; token: string }> = [];
    users.forEach((userSnap, index) => {
      stringList(userSnap.data()?.androidFcmTokens).forEach((token) => {
        tokenOwners.push({ uid: recipientDocs[index].recipientId, token });
      });
    });
    if (tokenOwners.length === 0) return;
    const deliveryTargets = tokenOwners.slice(0, 500);

    const payload: MulticastMessage = {
      tokens: deliveryTargets.map(({ token }) => token),
      data: {
        title,
        body: preview,
        message: preview,
        type: "new_message",
        link,
        roomId,
        notificationId: `chat_${roomId}`,
      },
      android: { priority: "high" },
    };
    const result = await messaging.sendEachForMulticast(payload);
    const invalidTokens = result.responses
      .map((response, index) => response.success ? null : ({
        uid: deliveryTargets[index].uid,
        token: deliveryTargets[index].token,
        code: response.error?.code ?? "",
      }))
      .filter((value): value is { uid: string; token: string; code: string } => value !== null)
      .filter(({ code }) => code.includes("registration-token-not-registered") || code.includes("invalid-registration-token"));

    await Promise.all(invalidTokens.map(async ({ uid, token }) => {
      const batch = db.batch();
      batch.set(
        db.collection("users").doc(uid),
        { androidFcmTokens: FieldValue.arrayRemove(token) },
        { merge: true },
      );
      batch.delete(db.collection("androidPushTokens").doc(tokenId(token)));
      await batch.commit();
    }));
  },
);

function requireAuth(uid: string | undefined): string {
  if (!uid) throw new HttpsError("unauthenticated", "Authentication is required.");
  return uid;
}

function validateToken(value: unknown): string {
  if (typeof value !== "string" || value.trim().length === 0 || value.length > MAX_TOKEN_LENGTH) {
    throw new HttpsError("invalid-argument", "A valid Android push token is required.");
  }
  return value.trim();
}

function isAlreadyExists(error: unknown): boolean {
  if (typeof error !== "object" || error === null || !("code" in error)) return false;
  const code = (error as { code?: unknown }).code;
  return code === 6 || code === "6" || code === "already-exists";
}

function tokenId(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}
