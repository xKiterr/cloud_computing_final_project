package grpcserverapp;

import cn2026.labels.contract.ImageBlock;
import cn2026.labels.contract.LabelsServiceGrpc;
import cn2026.labels.contract.SubmitResponse;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import io.grpc.stub.StreamObserver;

import java.util.UUID;

public class LabelsServiceImpl extends LabelsServiceGrpc.LabelsServiceImplBase {

    private final Storage storage;
    private final String bucketName = "cn2026-labels-bucket";

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
}