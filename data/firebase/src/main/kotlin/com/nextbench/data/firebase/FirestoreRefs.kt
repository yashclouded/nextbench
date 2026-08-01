package com.nextbench.data.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed Firestore collection/document reference accessors. Centralises path strings so
 * repositories never hard-code collection names.
 */
@Singleton
class FirestoreRefs @Inject constructor(private val db: FirebaseFirestore) {

    val users: CollectionReference get() = db.collection("users")
    val usernames: CollectionReference get() = db.collection("usernames")
    val posts: CollectionReference get() = db.collection("posts")
    val postReplies: CollectionReference get() = db.collection("post_replies")
    val postUpvotes: CollectionReference get() = db.collection("post_upvotes")
    val postDownvotes: CollectionReference get() = db.collection("post_downvotes")
    val postReactions: CollectionReference get() = db.collection("post_reactions")
    val savedPosts: CollectionReference get() = db.collection("saved_posts")
    val products: CollectionReference get() = db.collection("products")
    val wishlists: CollectionReference get() = db.collection("wishlists")
    val reviews: CollectionReference get() = db.collection("reviews")
    val chatRooms: CollectionReference get() = db.collection("chatRooms")
    val clubs: CollectionReference get() = db.collection("clubs")
    val stories: CollectionReference get() = db.collection("stories")
    val notifications: CollectionReference get() = db.collection("notifications")
    val follows: CollectionReference get() = db.collection("follows")
    val followEdges: CollectionReference get() = db.collection("follow_edges")
    val blocks: CollectionReference get() = db.collection("blocks")
    val reports: CollectionReference get() = db.collection("reports")
    val schools: CollectionReference get() = db.collection("schools")
    val schoolRequests: CollectionReference get() = db.collection("school_requests")
    val referrals: CollectionReference get() = db.collection("referrals")
    val linkPreviews: CollectionReference get() = db.collection("linkPreviews")

    fun user(uid: String): DocumentReference = users.document(uid)
    fun post(id: String): DocumentReference = posts.document(id)
    fun product(id: String): DocumentReference = products.document(id)
    fun chatRoom(id: String): DocumentReference = chatRooms.document(id)
    fun messages(roomId: String): CollectionReference = chatRooms.document(roomId).collection("messages")
    fun club(id: String): DocumentReference = clubs.document(id)
    fun clubMessages(clubId: String): CollectionReference = clubs.document(clubId).collection("messages")
}
