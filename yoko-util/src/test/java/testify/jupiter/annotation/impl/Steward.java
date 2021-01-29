/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package testify.jupiter.annotation.impl;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides the mechanism to create, store, and retrieve
 * an instance of an object for managing some orthogonal aspect of a
 * test or test case.
 *
 * Since this is to be used with an annotation, the annotated member
 * will not provide a location for the annotation to store its artifacts.
 * Instead, the Jupiter extension context store for a type-specific namespace
 * is retrieved, and the test class is used as the key.
 *
 * So, for a given extension context, artifact type, and annotated test class,
 * there will exist at most one such artifact. This artifact will be retrieved
 * and used by the implementing child class to allow state to be propagated from
 * one method invocation to another during the handling of the specified annotation
 *
 * @param <A> the annotation type to be used
 */
public class Steward<A extends Annotation> implements CloseableResource {
    private final AnnotationButler<A> butler;
    protected final A annotation;

    protected Steward(Class<A> annotationClass, AnnotatedElement elem) {
        this.butler = AnnotationButler.forClass(annotationClass).recruit();
        this.annotation = butler.getAnnotation(elem);
    }

    @Deprecated
    protected static <S extends Steward<?>> S getInstanceForContext(ExtensionContext ctx, Class<S> type, Function<Class<?>, S> constructor) {
        final Store store = ctx.getStore(Namespace.create(type));
        final Class<?> testClass = ctx.getRequiredTestClass();
        return store.getOrComputeIfAbsent(testClass, constructor, type);
    }

    protected static <S extends Steward<?>> Optional<S> getOrCreate(ExtensionContext ctx, Class<S> type, Function<AnnotatedElement, S> constructor) {
        final Store store = ctx.getStore(Namespace.create(type));
        Function<AnnotatedElement, S> factory = soften(constructor); // convert exceptions to a null result;
        return ctx.getElement().map(e -> store.getOrComputeIfAbsent(e, factory, type));
    }

    private static <T, U> Function<T, U> soften(Function<T, U> function) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception e) {
                return null;
            }
        };
    }


    /**
     * Child classes that have any clean up work to do should override this method.
     */
    @Override
    public void close() { /* do nothing */ }
}

//abstract class MultiSteward<A extends Annotation, C extends Annotation> extends Steward<C> implements Iterable<Steward<A>>{
//    private final List<Steward<A>> subStewards;
//
//    protected MultiSteward(Class<A> annotationClass, Class<C> containerAnnotationClass, Function<C, A[]> valueMethodRef, Class<?> annotatedClass) {
//        super(containerAnnotationClass, annotatedClass);
//        validateRepeatableRelationship(annotationClass, containerAnnotationClass);
//        this.subStewards = Collections.unmodifiableList(
//                Stream.of(valueMethodRef.apply(annotation))
//                        .map(c -> )
//                        .collect(Collectors.toList()));
//
//    }
//
//    private static <A extends Annotation, B extends Annotation> void validateRepeatableRelationship(Class<B> annotationClass, Class<A> containerAnnotationClass) {
//        Repeatable repeatable = annotationClass.getAnnotation(Repeatable.class);
//        if (repeatable != null && repeatable.value() == containerAnnotationClass) return;
//        throw new Error(annotationClass + " does not declare @Repeatable(" + containerAnnotationClass.getSimpleName() + ")");
//    }
//
//
//}