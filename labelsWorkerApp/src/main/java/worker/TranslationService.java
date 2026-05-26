package worker;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationService {

    public List<String> translateLabels(List<String> labels) {
        List<String> translatedLabels = new ArrayList<>();

        Translate translateService = TranslateOptions.getDefaultInstance().getService();

        for (String label : labels) {
            Translation translation = translateService.translate(
                    label,
                    Translate.TranslateOption.sourceLanguage("en"),
                    Translate.TranslateOption.targetLanguage("pt")
            );

            translatedLabels.add(translation.getTranslatedText());
        }

        return translatedLabels;
    }
}