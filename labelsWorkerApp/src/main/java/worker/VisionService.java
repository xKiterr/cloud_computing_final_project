package worker;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VisionService {

    public List<String> detectLabels(String gsUri) throws IOException {
        List<String> labels = new ArrayList<>();

        Image img = Image.newBuilder()
                .setSource(ImageSource.newBuilder().setImageUri(gsUri).build())
                .build();

        Feature feat = Feature.newBuilder()
                .setType(Feature.Type.LABEL_DETECTION)
                .build();

        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feat)
                .setImage(img)
                .build();

        List<AnnotateImageRequest> requests = new ArrayList<>();
        requests.add(request);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);

            for (AnnotateImageResponse res : response.getResponsesList()) {
                if (res.hasError()) {
                    System.out.println("Vision API error: " + res.getError().getMessage());
                    continue;
                }

                for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                    labels.add(annotation.getDescription());
                }
            }
        }

        return labels;
    }
}