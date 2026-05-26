package worker;

public class AppMain {
    public static void main(String[] args) {
        System.out.println("Labels Worker started");

        if (args.length != 2) {
            System.out.println("Use: <projectId> <subscriptionId>");
            return;
        }

        String projectId = args[0];
        String subscriptionId = args[1];

        PubSubWorker worker = new PubSubWorker(projectId, subscriptionId);
        worker.start();
    }
}