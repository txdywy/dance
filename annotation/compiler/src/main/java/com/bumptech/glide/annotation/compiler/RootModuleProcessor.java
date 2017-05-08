package com.bumptech.glide.annotation.compiler;

import com.bumptech.glide.annotation.GlideModule;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Runs the final steps of Glide's annotation process and generates the combined
 * {@link com.bumptech.glide.module.RootGlideModule}, {@link com.bumptech.glide.Glide},
 * {@link com.bumptech.glide.RequestManager}, and
 * {@link com.bumptech.glide.request.BaseRequestOptions} classes.
 */
final class RootModuleProcessor {
  private static final String COMPILER_PACKAGE_NAME =
      GlideAnnotationProcessor.class.getPackage().getName();

  private final ProcessingEnvironment processingEnv;
  private final ProcessorUtil processorUtil;
  private final List<TypeElement> rootGlideModules = new ArrayList<>();
  private final RequestOptionsGenerator requestOptionsGenerator;
  private final RequestManagerGenerator requestManagerGenerator;
  private final RootModuleGenerator rootModuleGenerator;
  private final RequestBuilderGenerator requestBuilderGenerator;
  private final RequestManagerFactoryGenerator requestManagerFactoryGenerator;
  private final GlideGenerator glideGenerator;

  RootModuleProcessor(ProcessingEnvironment processingEnv, ProcessorUtil processorUtil) {
    this.processingEnv = processingEnv;
    this.processorUtil = processorUtil;

    rootModuleGenerator = new RootModuleGenerator(processorUtil);
    requestOptionsGenerator = new RequestOptionsGenerator(processingEnv, processorUtil);
    requestManagerGenerator = new RequestManagerGenerator(processingEnv, processorUtil);
    requestManagerFactoryGenerator = new RequestManagerFactoryGenerator(processingEnv);
    glideGenerator = new GlideGenerator(processingEnv, processorUtil);
    requestBuilderGenerator = new RequestBuilderGenerator(processingEnv, processorUtil);
  }

  void processModules(Set<? extends TypeElement> set, RoundEnvironment env) {
     for (TypeElement element : processorUtil.getElementsFor(GlideModule.class, env)) {
       if (processorUtil.isRootGlideModule(element)) {
         rootGlideModules.add(element);
       }
     }

    processorUtil.debugLog("got root modules: " + rootGlideModules);

    if (rootGlideModules.size() > 1) {
      throw new IllegalStateException(
          "You cannot have more than one RootGlideModule, found: " + rootGlideModules);
    }
  }

  boolean maybeWriteRootModule() {
    // rootGlideModules is added to in order to catch errors where multiple RootGlideModules may be
    // present for a single application or library. Because we only add to rootGlideModules, we use
    // isGeneratedRootGlideModuleWritten to make sure the GeneratedRootGlideModule is written at
    // most once.
    if (rootGlideModules.isEmpty()) {
      return false;
    }
    TypeElement rootModule = rootGlideModules.get(0);
    processorUtil.debugLog("Processing root module: " + rootModule);
    // If this package is null, it means there are no classes with this package name. One way this
    // could happen is if we process an annotation and reach this point without writing something
    // to the package. We do not error check here because that shouldn't happen with the
    // current implementation.
    PackageElement glideGenPackage =
        processingEnv.getElementUtils().getPackageElement(COMPILER_PACKAGE_NAME);
    FoundIndexedClassNames indexedClassNames = getIndexedClassNames(glideGenPackage);

    // Write all generated code to the package containing the RootGlideModule. Doing so fixes
    // classpath collisions if more than one Application containing a RootGlideModule is included
    // in a project.
    String generatedCodePackageName = rootModule.getEnclosingElement().toString();

    TypeSpec generatedRequestOptions = null;
    if (!indexedClassNames.extensions.isEmpty()) {
      generatedRequestOptions =
          requestOptionsGenerator.generate(generatedCodePackageName, indexedClassNames.extensions);
      writeRequestOptions(generatedCodePackageName, generatedRequestOptions);
    }

    TypeSpec generatedRequestBuilder =
        requestBuilderGenerator.generate(generatedCodePackageName, generatedRequestOptions);
    writeRequestBuilder(generatedCodePackageName, generatedRequestBuilder);

    TypeSpec requestManager =
        requestManagerGenerator.generate(
            generatedCodePackageName, generatedRequestOptions, generatedRequestBuilder,
            indexedClassNames.extensions);
    writeRequestManager(generatedCodePackageName, requestManager);

    TypeSpec requestManagerFactory =
        requestManagerFactoryGenerator.generate(generatedCodePackageName, requestManager);
    writeRequestManagerFactory(requestManagerFactory);

    TypeSpec glide =
        glideGenerator.generate(generatedCodePackageName, getGlideName(rootModule), requestManager);
    writeGlide(generatedCodePackageName, glide);

    TypeSpec generatedRootGlideModule =
        rootModuleGenerator.generate(rootModule, indexedClassNames.glideModules);
    writeRootModule(generatedRootGlideModule);

    processorUtil.infoLog("Wrote GeneratedRootGlideModule with: " + indexedClassNames.glideModules);

    return true;
  }

  private String getGlideName(TypeElement rootModule) {
    return rootModule.getAnnotation(GlideModule.class).glideName();
  }

  @SuppressWarnings("unchecked")
  private FoundIndexedClassNames getIndexedClassNames(PackageElement glideGenPackage) {
    Set<String> glideModules = new HashSet<>();
    Set<String> extensions = new HashSet<>();
    List<? extends Element> glideGeneratedElements = glideGenPackage.getEnclosedElements();
    for (Element indexer : glideGeneratedElements) {
      Index annotation = indexer.getAnnotation(Index.class);
      // If the annotation is null, it means we've come across another class in the same package
      // that we can safely ignore.
      if (annotation != null) {
        Collections.addAll(glideModules, annotation.modules());
        Collections.addAll(extensions, annotation.extensions());
      }
    }

    processorUtil.debugLog("Found GlideModules: " + glideModules);
    return new FoundIndexedClassNames(glideModules, extensions);
  }

  private void writeGlide(String packageName, TypeSpec glide) {
    processorUtil.writeClass(packageName, glide);
  }

  private void writeRequestManager(String packageName, TypeSpec requestManager) {
    processorUtil.writeClass(packageName, requestManager);
  }

  // We dont' care about collisions in IDEs since this class isn't an API class.
  private void writeRequestManagerFactory(TypeSpec requestManagerFactory) {
    processorUtil.writeClass(
        RootModuleGenerator.GENERATED_ROOT_MODULE_PACKAGE_NAME, requestManagerFactory);
  }

  // The root module we generate subclasses a package private class. We don't care about classpath
  // collisions in IDEs since this class isn't an API class.
  private void writeRootModule(TypeSpec rootModule) {
    processorUtil.writeClass(RootModuleGenerator.GENERATED_ROOT_MODULE_PACKAGE_NAME, rootModule);
  }

  private void writeRequestOptions(String packageName, TypeSpec requestOptions) {
    processorUtil.writeClass(packageName, requestOptions);
  }

  private void writeRequestBuilder(String packageName, TypeSpec requestBuilder) {
    processorUtil.writeClass(packageName, requestBuilder);
  }

  private static final class FoundIndexedClassNames {
    final Set<String> glideModules;
    final Set<String> extensions;

    private FoundIndexedClassNames(Set<String> glideModules, Set<String> extensions) {
      this.glideModules = glideModules;
      this.extensions = extensions;
    }
  }
}
