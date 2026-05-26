package grpcclientapp;

import cn2026.labels.contract.LabelsServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) {
        String lookupUrl = "https://grpc-lookup-52998376201.europe-southwest1.run.app";
        String targetIp = "localhost"; //fallback

        System.out.println("Fetching active server IP from Cloud Run...");
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lookupUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                targetIp = response.body().trim();
                System.out.println("Successfully found active server: " + targetIp);
            } else {
                System.err.println("Lookup failed (HTTP " + response.statusCode() + "). Check your Cloud Run logs.");
                return;
            }
        } catch (Exception e) {
            System.err.println("Error contacting lookup service: " + e.getMessage());
            return;
        }

        String target = targetIp + ":8080";
        System.out.println("Connecting to gRPC server at " + target + "...");

        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();

        LabelsServiceGrpc.LabelsServiceStub asyncStub = LabelsServiceGrpc.newStub(channel);
        ImageClient imageClient = new ImageClient(asyncStub);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("\nLabels Application Client");

        while (running) {
            System.out.println("\nSelect an operation:");
            System.out.println("1. Submit image for processing");
            System.out.println("2. Check result by Request ID (Not Implemented)");
            System.out.println("3. Search images by Date/Label (Not Implemented)");
            System.out.println("0. Exit");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.print("Enter the absolute path to the image file: ");
                    String filePath = scanner.nextLine();
                    try {
                        imageClient.uploadImage(filePath);
                    } catch (Exception e) {
                        System.err.println("Upload error: " + e.getMessage());
                    }
                    break;
                case "2":
                    System.out.println("SF2: To be implemented (Firestore integration)");
                    break;
                case "3":
                    System.out.println("SF3: To be implemented (Firestore integration)");
                    break;
                case "0":
                    System.out.println("Shutting down client...");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Try again.");
            }
        }

        channel.shutdownNow();
    }
}