package grpcclientapp;

import cn2026.labels.contract.ImageBlock;
import cn2026.labels.contract.LabelsServiceGrpc;
import cn2026.labels.contract.SubmitResponse;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

public class ImageClient {
    private final LabelsServiceGrpc.LabelsServiceStub asyncStub;

    public ImageClient(LabelsServiceGrpc.LabelsServiceStub asyncStub) {
        this.asyncStub = asyncStub;
    }

    public void uploadImage(String filePath) throws InterruptedException {
        CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<SubmitResponse> responseObserver = new StreamObserver<SubmitResponse>() {
            @Override
            public void onNext(SubmitResponse response) {
                System.out.println("Upload complete! Your Request ID is: " + response.getRequestId());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Upload failed: " + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };

        StreamObserver<ImageBlock> requestObserver = asyncStub.submitImage(responseObserver);

        try {
            Path path = Paths.get(filePath);
            InputStream inputStream = Files.newInputStream(path);
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            boolean isFirstBlock = true;

            System.out.println("Uploading " + path.getFileName() + " in blocks...");

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                ImageBlock.Builder blockBuilder = ImageBlock.newBuilder()
                        .setChunk(ByteString.copyFrom(buffer, 0, bytesRead));

                if (isFirstBlock) {
                    blockBuilder.setFileName(path.getFileName().toString());
                    isFirstBlock = false;
                }

                requestObserver.onNext(blockBuilder.build());
            }

            requestObserver.onCompleted();
            inputStream.close();
        } catch (Exception e) {
            requestObserver.onError(e);
        }

        finishLatch.await();
    }
}