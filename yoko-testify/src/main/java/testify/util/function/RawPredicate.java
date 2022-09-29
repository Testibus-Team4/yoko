/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     IBM Corporation - Initial implementation
*******************************************************************************/

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
package testify.util.function;

import org.opentest4j.AssertionFailedError;

import java.util.Objects;

@FunctionalInterface
public interface RawPredicate<T> extends java.util.function.Predicate<T> {
    boolean testRaw(T t) throws Exception;

    default boolean test(T t) {
        try {
            return testRaw(t);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionFailedError("", e);
        }

    }

    default RawPredicate<T> negate() { return t -> !this.testRaw(t); }

    default RawPredicate<T> and(RawPredicate<? super T> that) {
        return t -> this.testRaw(t) && that.testRaw(t);
    }

    default RawPredicate<T> or(RawPredicate<? super T> that) {
        return t -> this.testRaw(t) || that.testRaw(t);
    }

    static <T> RawPredicate<T> isEqual(Object targetRef) {
        return t -> Objects.equals(targetRef, t);
    }
}
