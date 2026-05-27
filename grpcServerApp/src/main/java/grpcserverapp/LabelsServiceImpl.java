package grpcserverapp;

import cn2026.labels.contract.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.WriteChannel;
import com.google.cloud.firestore.*;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LabelsServiceImpl extends LabelsServiceGrpc.LabelsServiceImplBase {

    private final Storage storage;
    private final String bucketName = "cn2526-labels-bucket";

    public LabelsServiceImpl(Storage storage) {
        this.storage = storage;
    }

    @Override
    public StreamObserver<ImageBlock> submitImage(StreamObserver<SubmitResponse> responseObserver) {
        return new StreamObserver<ImageBlock>() {

            WriteChannel writeChannel = null;
            String fileName = null;

            @Override
            public void onNext(ImageBlock value) {
                try {
                    if (writeChannel == null) {
                        fileName = value.getFileName();
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = "unknown_image_" + System.currentTimeMillis() + ".jpg";
                        }

                        BlobId blobId = BlobId.of(bucketName, fileName);
                        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
                        writeChannel = storage.writer(blobInfo);
                        System.out.println("Started receiving file: " + fileName);
                    }

                    writeChannel.write(value.getChunk().asReadOnlyByteBuffer());

                } catch (Exception e) {
                    System.err.println("Error writing chunk: " + e.getMessage());
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Client cancelled or error occurred: " + t.getMessage());
                if (writeChannel != null) {
                    try { writeChannel.close(); } catch (Exception ignored) {}
                }
            }

            @Override
            public void onCompleted() {
                try {
                    if (writeChannel != null) {
                        writeChannel.close();
                    }

                    String requestId = UUID.randomUUID().toString();
                    System.out.println("Successfully uploaded " + fileName + ". Assigned ID: " + requestId);

                    String projectId = "cn2526-t2-g10";
                    String topicId = "cn2026-image-processing";

                    TopicName topicName = TopicName.of(projectId, topicId);
                    Publisher publisher = null;

                    try {
                        publisher = Publisher.newBuilder(topicName).build();

                        String messagePayload = String.format(
                                "{\"requestId\":\"%s\", \"bucketName\":\"%s\", \"blobName\":\"%s\"}",
                                requestId, bucketName, fileName
                        );

                        ByteString data = ByteString.copyFromUtf8(messagePayload);
                        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                                .setData(data)
                                .build();

                        publisher.publish(pubsubMessage).get();
                        System.out.println("Published processing request to Pub/Sub for ID: " + requestId);

                    } catch (Exception e) {
                        System.err.println("Failed to publish message: " + e.getMessage());
                        responseObserver.onError(io.grpc.Status.INTERNAL
                                .withDescription("Failed to queue image for processing.")
                                .asRuntimeException());
                        return;
                    } finally {
                        if (publisher != null) {
                            publisher.shutdown();
                        }
                    }

                    SubmitResponse response = SubmitResponse.newBuilder()
                            .setRequestId(requestId)
                            .build();

                    responseObserver.onNext(response);
                    responseObserver.onCompleted();

                } catch (Exception e) {
                    responseObserver.onError(e);
                }
            }
        };
    }

    @Override
    public void getProcessingResult(ResultRequest request, StreamObserver<ResultResponse> responseObserver) {
        try {
            Firestore db = FirestoreOptions.newBuilder()
                    .setDatabaseId("cn2526-t2-g10-db")
                    .build()
                    .getService();

            DocumentSnapshot document = db.collection("image-results")
                    .document(request.getRequestId())
                    .get()
                    .get();

            if (document.exists()) {
                String dateProcessed = document.getTimestamp("processedDate").toString();
                List<String> labelsEn = (List<String>) document.get("labelsEn");
                List<String> labelsPt = (List<String>) document.get("labelsPt");

                Map<String, String> labelsMap = new HashMap<>();
                if (labelsEn != null && labelsPt != null && labelsEn.size() == labelsPt.size()) {
                    for (int i = 0; i < labelsEn.size(); i++) {
                        labelsMap.put(labelsEn.get(i), labelsPt.get(i));
                    }
                } else {
                    System.err.println("Warning: Label arrays are missing or mismatched for ID: " + request.getRequestId());
                }

                ResultResponse response = ResultResponse.newBuilder()
                        .setDateProcessed(dateProcessed)
                        .putAllLabels(labelsMap)
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Request ID not found. It might still be processing or the ID is wrong.")
                        .asRuntimeException());
            }
        } catch (Exception e) {
            System.err.println("Firestore Error: " + e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Server error while querying database.")
                    .asRuntimeException());
        }


    }

    @Override
    public void searchImages(SearchRequest request, StreamObserver<SearchResult> responseObserver) {
        try {
            Firestore db = FirestoreOptions.newBuilder()
                    .setDatabaseId("cn2526-t2-g10-db")
                    .build()
                    .getService();
            String keyword = request.getCharacteristic().toLowerCase().trim();

            if (keyword.isEmpty()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Search characteristic cannot be empty.")
                        .asRuntimeException());
                return;
            }

            Query query = db.collection("image-results")
                    .where(Filter.or(
                            Filter.arrayContains("labelsEn", keyword),
                            Filter.arrayContains("labelsPt", keyword)
                    ));

            Timestamp startTimestamp = null;
            Timestamp endTimestamp = null;

            try {
                String startDateStr = request.getStartDate();
                if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                    startTimestamp = Timestamp.parseTimestamp(startDateStr + "T00:00:00Z");
                }

                String endDateStr = request.getEndDate();
                if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                    endTimestamp = Timestamp.parseTimestamp(endDateStr + "T23:59:59Z");
                }
            } catch (Exception e) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Invalid date format. Please use exactly YYYY-MM-DD.")
                        .asRuntimeException());
                return;
            }

            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            boolean found = false;
            for (QueryDocumentSnapshot document : documents) {
                Timestamp docDate = document.getTimestamp("processedDate");

                if (startTimestamp != null && docDate.compareTo(startTimestamp) < 0) {
                    continue;
                }

                if (endTimestamp != null && docDate.compareTo(endTimestamp) > 0) {
                    continue;
                }

                String dateProcessed = docDate.toDate().toString();
                String fileName = document.getString("blobName");

                SearchResult result = SearchResult.newBuilder()
                        .setFileName(fileName != null ? fileName : "Unknown File")
                        .setDateProcessed(dateProcessed)
                        .build();

                responseObserver.onNext(result);
                found = true;
            }

            if (!found) {
                System.out.println("No images found containing the label: " + keyword);
            }

            responseObserver.onCompleted();

        } catch (Exception e) {
            System.err.println("Search Error: " + e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Server error during search.")
                    .asRuntimeException());
        }
    }
}