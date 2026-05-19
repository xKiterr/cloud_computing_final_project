package grpcclientapp;

import cn2026.labels.contract.LabelsServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;

public class Client {

    public static void main(String[] args) {
        String target = "localhost:8080";
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