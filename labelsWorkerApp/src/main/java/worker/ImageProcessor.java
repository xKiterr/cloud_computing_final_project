package worker;

import java.util.List;

public class ImageProcessor {

    private final VisionService visionService;
    private final TranslationService translationService;
    private final FirestoreService firestoreService;

    public ImageProcessor() {
        this.visionService = new VisionService();
        this.translationService = new TranslationService();
        this.firestoreService = new FirestoreService();
    }

    public void process(ProcessingRequest request) throws Exception {
        String gsUri = "gs://" + request.bucketName + "/" + request.blobName;

        System.out.println("Processing request: " + request.requestId);
        System.out.println("Image URI: " + gsUri);

        List<String> labels = visionService.detectLabels(gsUri);
        List<String> translatedLabels = translationService.translateLabels(labels);

        System.out.println("Detected and translated labels:");
        for (int i = 0; i < labels.size(); i++) {
            System.out.println("- " + labels.get(i) + " -> " + translatedLabels.get(i));
        }

        firestoreService.saveResult(
                request.requestId,
                request.bucketName,
                request.blobName,
                gsUri,
                labels,
                translatedLabels
        );
    }
}