package grpcclientapp;

import cn2026.labels.contract.*;
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

    public void checkResult(String requestId) throws InterruptedException {
        CountDownLatch finishLatch = new CountDownLatch(1);

        ResultRequest request = ResultRequest.newBuilder()
                .setRequestId(requestId)
                .build();

        asyncStub.getProcessingResult(request, new StreamObserver<ResultResponse>() {
            @Override
            public void onNext(cn2026.labels.contract.ResultResponse response) {
                System.out.println("\nProcessing Results");
                System.out.println("Date Processed: " + response.getDateProcessed());
                System.out.println("Labels:");

                response.getLabelsMap().forEach((en, pt) -> {
                    System.out.println("- " + en + " -> " + pt);
                });
                System.out.println();
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error fetching result: " + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        });

        finishLatch.await();
    }

    public void searchDatabase(String startDate, String endDate, String keyword) throws InterruptedException {
        CountDownLatch finishLatch = new CountDownLatch(1);

        SearchRequest request = SearchRequest.newBuilder()
                .setStartDate(startDate)
                .setEndDate(endDate)
                .setCharacteristic(keyword)
                .build();

        System.out.println("\nSearch Results for '" + keyword);

        asyncStub.searchImages(request, new StreamObserver<SearchResult>() {
            boolean foundAny = false;

            @Override
            public void onNext(SearchResult result) {
                foundAny = true;
                System.out.println("- File: " + result.getFileName() + " | Processed: " + result.getDateProcessed());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Search failed: " + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                if (!foundAny) {
                    System.out.println("No matching images found.");
                }
                finishLatch.countDown();
            }
        });

        finishLatch.await();
    }
}