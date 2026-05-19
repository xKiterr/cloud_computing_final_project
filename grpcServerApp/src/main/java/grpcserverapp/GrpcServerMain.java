package grpcserverapp;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class GrpcServerMain {
    public static void main(String[] args) {
        try {
            int port = 8080;
            Storage storage = StorageOptions.getDefaultInstance().getService();

            System.out.println("Starting gRPC Server on port " + port + "...");
            Server server = ServerBuilder.forPort(port)
                    .addService(new LabelsServiceImpl(storage))
                    .build()
                    .start();

            System.out.println("Server is online and listening for image uploads");

            Runtime.getRuntime().addShutdownHook(new ShutdownHook(server));

            server.awaitTermination();

        } catch (Exception e) {
            System.err.println("Server crashed during startup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}