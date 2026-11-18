package bg.hristomanov.education.quarkus.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Singleton;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import bg.hristomanov.education.quarkus.runtime.GreetingDefinition;
import bg.hristomanov.education.quarkus.runtime.GreetingMessage;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodCreator;

/**
 * Jandex чете metadata при augmentation, а Gizmo създава CDI implementation.
 * Deployment class-овете не са част от runtime application classpath.
 */
public final class GreetingBuildProcessor {
    private static final DotName MARKER = DotName.createSimple(GreetingDefinition.class.getName());
    private static final String GENERATED_CLASS =
            "bg.hristomanov.education.quarkus.generated.GeneratedGreetingMessage";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("education-generated-greeting");
    }

    @BuildStep
    GreetingModelBuildItem indexGreeting(CombinedIndexBuildItem indexBuildItem) {
        IndexView index = indexBuildItem.getIndex();
        List<String> messages = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(MARKER)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                throw new IllegalStateException("@GreetingDefinition is only supported on classes");
            }
            messages.add(annotation.value().asString());
        }
        return new GreetingModelBuildItem(GreetingRules.requireSingle(messages));
    }

    @BuildStep
    void generate(GreetingModelBuildItem model, BuildProducer<GeneratedBeanBuildItem> beans) {
        // GeneratedBeanGizmoAdaptor дава реален CDI bean, не просто Java source пример.
        try (ClassCreator creator = ClassCreator.builder()
                .classOutput(new GeneratedBeanGizmoAdaptor(beans))
                .className(GENERATED_CLASS)
                .interfaces(GreetingMessage.class)
                .build()) {
            creator.addAnnotation(Singleton.class);
            MethodCreator method = creator.getMethodCreator("message", String.class);
            method.setModifiers(Modifier.PUBLIC);
            method.returnValue(method.load(model.message()));
        }
    }
}
