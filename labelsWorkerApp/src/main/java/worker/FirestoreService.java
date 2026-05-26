package worker;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.WriteResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreService {

    private final Firestore db;

    public FirestoreService() {
        this.db = FirestoreOptions
                .newBuilder()
                .setDatabaseId("cn2526-t2-g10-db")
                .build()
                .getService();
    }

    public void saveResult(
            String requestId,
            String bucketName,
            String blobName,
            String imageUri,
            List<String> labelsEn,
            List<String> labelsPt
    ) throws Exception {

        Map<String, Object> document = new HashMap<>();

        document.put("requestId", requestId);
        document.put("bucketName", bucketName);
        document.put("blobName", blobName);
        document.put("imageUri", imageUri);
        document.put("status", "DONE");
        document.put("processedDate", Timestamp.now());
        document.put("labelsEn", labelsEn);
        document.put("labelsPt", labelsPt);

        DocumentReference docRef = db
                .collection("image-results")
                .document(requestId);

        ApiFuture<WriteResult> result = docRef.set(document);

        System.out.println("Saved to Firestore. Update time: " + result.get().getUpdateTime());
    }
}