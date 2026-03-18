package com.example.opticdoorbell

import com.google.mlkit.vision.face.Face
import kotlin.math.sqrt

/**
 * On-device face recognition using ML Kit face landmarks as embeddings.
 * No external model needed — uses relative positions of facial landmarks
 * (eyes, nose, ears) to create a simple geometric signature for each face.
 */
object FaceRecognitionHelper {

    data class EnrolledFace(val name: String, val embedding: FloatArray)

    private val enrolledFaces = mutableListOf<EnrolledFace>()

    // Similarity threshold — lower = stricter matching
    private const val THRESHOLD = 0.82f

    /**
     * Enroll a new person. Call this when the user takes a photo and provides a name.
     */
    fun enroll(name: String, face: Face) {
        val embedding = extractEmbedding(face) ?: return
        // Remove existing entry with same name
        enrolledFaces.removeAll { it.name == name }
        enrolledFaces.add(EnrolledFace(name, embedding))
    }

    /**
     * Try to recognise a detected face. Returns the name or null if unknown.
     */
    fun recognize(face: Face): String? {
        if (enrolledFaces.isEmpty()) return null
        val embedding = extractEmbedding(face) ?: return null

        var bestName: String? = null
        var bestScore = 0f

        for (enrolled in enrolledFaces) {
            val score = cosineSimilarity(embedding, enrolled.embedding)
            if (score > bestScore) {
                bestScore = score
                bestName = enrolled.name
            }
        }

        return if (bestScore >= THRESHOLD) bestName else null
    }

    fun getEnrolledNames(): List<String> = enrolledFaces.map { it.name }

    fun removeEnrolled(name: String) {
        enrolledFaces.removeAll { it.name == name }
    }

    fun clear() = enrolledFaces.clear()

    /**
     * Extracts a geometric embedding from face landmarks.
     * Uses relative positions of eyes, nose tip, and mouth corners
     * normalised by face bounding box size for scale invariance.
     */
    private fun extractEmbedding(face: Face): FloatArray? {
        val box = face.boundingBox
        val w = box.width().toFloat()
        val h = box.height().toFloat()
        if (w == 0f || h == 0f) return null

        val features = mutableListOf<Float>()

        // Left eye position (normalised)
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.let {
            features.add((it.position.x - box.left) / w)
            features.add((it.position.y - box.top) / h)
        } ?: run { features.add(0f); features.add(0f) }

        // Right eye position (normalised)
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.let {
            features.add((it.position.x - box.left) / w)
            features.add((it.position.y - box.top) / h)
        } ?: run { features.add(0f); features.add(0f) }

        // Nose base
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE)?.let {
            features.add((it.position.x - box.left) / w)
            features.add((it.position.y - box.top) / h)
        } ?: run { features.add(0f); features.add(0f) }

        // Mouth left
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT)?.let {
            features.add((it.position.x - box.left) / w)
            features.add((it.position.y - box.top) / h)
        } ?: run { features.add(0f); features.add(0f) }

        // Mouth right
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT)?.let {
            features.add((it.position.x - box.left) / w)
            features.add((it.position.y - box.top) / h)
        } ?: run { features.add(0f); features.add(0f) }

        // Left ear
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EAR)?.let {
            features.add((it.position.x - box.left) / w)
            features.add((it.position.y - box.top) / h)
        } ?: run { features.add(0f); features.add(0f) }

        // Right ear
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EAR)?.let {
            features.add((it.position.x - box.left) / w)
            features.add((it.position.y - box.top) / h)
        } ?: run { features.add(0f); features.add(0f) }

        // Head euler angles for pose
        features.add(face.headEulerAngleX / 90f)
        features.add(face.headEulerAngleY / 90f)
        features.add(face.headEulerAngleZ / 90f)

        return features.toFloatArray()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}