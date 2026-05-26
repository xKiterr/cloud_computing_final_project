package worker;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;

public class PubSubWorker {

    private final String projectId;
    private final String subscriptionId;
    private final ImageProcessor processor;
    private final Gson gson;

    public PubSubWorker(String projectId, String subscriptionId) {
        this.projectId = projectId;
        this.subscriptionId = subscriptionId;
        this.processor = new ImageProcessor();
        this.gson = new Gson();
    }

    public void start() {
        ProjectSubscriptionName subscriptionName =
                ProjectSubscriptionName.of(projectId, subscriptionId);

        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            String json = message.getData().toStringUtf8();

            System.out.println("Received Pub/Sub message:");
            System.out.println(json);

            try {
                ProcessingRequest request = gson.fromJson(json, ProcessingRequest.class);

                processor.process(request);

                consumer.ack();
                System.out.println("Message acknowledged: " + request.requestId);

            } catch (Exception e) {
                System.out.println("Error while processing message. Message will be returned to queue.");
                e.printStackTrace();

                consumer.nack();
            }
        };

        Subscriber subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();

        subscriber.startAsync().awaitRunning();

        System.out.println("Worker started. Listening on subscription: " + subscriptionId);

        try {
            subscriber.awaitTerminated();
        } catch (Exception e) {
            System.out.println("Subscriber stopped.");
            e.printStackTrace();
        }
    }
}