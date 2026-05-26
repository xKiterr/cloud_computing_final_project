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
import com.google.cloud.compute.v1.InstanceGroupManagersClient;
import com.google.cloud.compute.v1.Operation;
import com.google.api.gax.longrunning.OperationFuture;

public class Client {

    public static void main(String[] args) {
        String lookupUrl = "https://grpc-lookup-52998376201.europe-southwest1.run.app";
        String targetIp = "localhost"; //fallback

        String projectId = "cn2526-t2-g10";
        String zone = "europe-southwest1-a";
        String grpcMigName = "grpc-server-mig";
        String workerMigName = "labels-app-mig";

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
                String[] availableIps = response.body().trim().split(",");

                System.out.println("\nAvailable active gRPC servers:");
                for (int i = 0; i < availableIps.length; i++) {
                    System.out.println((i + 1) + ". " + availableIps[i]);
                }

                Scanner scanner = new Scanner(System.in);
                System.out.print("\nSelect a server to connect to (1-" + availableIps.length + "): ");
                int choice = Integer.parseInt(scanner.nextLine());

                targetIp = availableIps[choice - 1];
                System.out.println("Selected server: " + targetIp);
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
            System.out.println("4. Change VM quantity for gRPC Server Group");
            System.out.println("5. Change VM quantity for Labels App Group");
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
                    System.out.print("Enter your Request ID: ");
                    String requestId = scanner.nextLine();
                    try {
                        imageClient.checkResult(requestId);
                    } catch (Exception e) {
                        System.err.println("Failed to get result: " + e.getMessage());
                    }
                    break;
                case "3":
                    System.out.println("Search Images");
                    System.out.print("Enter start date (YYYY-MM-DD) or press Enter to skip: ");
                    String startDate = scanner.nextLine();

                    System.out.print("Enter end date (YYYY-MM-DD) or press Enter to skip: ");
                    String endDate = scanner.nextLine();

                    System.out.print("Enter characteristic to search for (e.g., 'cat', 'car'): ");
                    String keyword = scanner.nextLine();

                    try {
                        imageClient.searchDatabase(startDate, endDate, keyword);
                    } catch (Exception e) {
                        System.err.println("Search error: " + e.getMessage());
                    }
                    break;
                case "4":
                    System.out.print("Enter new VM quantity for the gRPC Server Group: ");
                    int grpcSize = Integer.parseInt(scanner.nextLine());
                    resizeManagedInstanceGroup(projectId, zone, grpcMigName, grpcSize);
                    break;

                case "5":
                    System.out.print("Enter new VM quantity for the Labels App Worker Group: ");
                    int workerSize = Integer.parseInt(scanner.nextLine());
                    resizeManagedInstanceGroup(projectId, zone, workerMigName, workerSize);
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

    static void resizeManagedInstanceGroup(String project, String zone, String instanceGroupName, int newSize) {
        System.out.println("Resizing instance group '" + instanceGroupName + "' to " + newSize + " VMs...");
        try (InstanceGroupManagersClient managersClient = InstanceGroupManagersClient.create()) {
            OperationFuture<Operation, Operation> result = managersClient.resizeAsync(
                    project, zone, instanceGroupName, newSize
            );
            Operation oper = result.get();
            System.out.println("Resizing completed with status: " + oper.getStatus());
        } catch (Exception e) {
            System.err.println("Failed to resize group: " + e.getMessage());
        }
    }
}