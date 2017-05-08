package com.bumptech.glide.annotation.compiler;

import com.bumptech.glide.annotation.GlideModule;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Generates Indexer classes annotated with {@link Index} for all
 * {@link com.bumptech.glide.module.ChildGlideModule}s.
 */
final class ChildModuleProcessor {
  private ProcessorUtil processorUtil;
  private IndexerGenerator indexerGenerator;

  ChildModuleProcessor(ProcessorUtil processorUtil, IndexerGenerator indexerGenerator) {
    this.processorUtil = processorUtil;
    this.indexerGenerator = indexerGenerator;
  }

  boolean processModules(Set<? extends TypeElement> set, RoundEnvironment env) {
     // Order matters here, if we find an Indexer below, we return before writing the root module.
    // If we fail to add to rootModules before then, we might accidentally skip a valid RootModule.
    List<TypeElement> childGlideModules = new ArrayList<>();
    for (TypeElement element : processorUtil.getElementsFor(GlideModule.class, env)) {
      // Root elements are added separately and must be checked separately because they're sub
      // classes of ChildGlideModules.
      if (processorUtil.isRootGlideModule(element)) {
        continue;
      } else if (!processorUtil.isChildGlideModule(element)) {
        throw new IllegalStateException("@GlideModule can only be applied to ChildGlideModule"
            + " and RootGlideModule implementations, not: " + element);
      }

      childGlideModules.add(element);
    }

    processorUtil.debugLog("got child modules: " + childGlideModules);
    if (childGlideModules.isEmpty()) {
      return false;
    }

    TypeSpec indexer = indexerGenerator.generate(childGlideModules);
    processorUtil.writeIndexer(indexer);
    processorUtil.debugLog("Wrote an Indexer this round, skipping the root module to ensure all "
        + "indexers are found");
     // If I write an Indexer in a round in the target package, then try to find all classes in
    // the target package, my newly written Indexer won't be found. Since we wrote a class with
    // an Annotation handled by this processor, we know we will be called again in the next round
    // and we can safely wait to write our RootModule until then.
    return true;
  }

  Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(GlideModule.class.getName());
  }
}
