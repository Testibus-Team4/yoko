/*==============================================================================
 * Copyright 2022 IBM Corporation and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *=============================================================================*/

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
package testify.matchers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

@SuppressWarnings("unchecked")
public enum Matchers {
    ;
    public static <T> Matcher<Iterable<T>> consistsOf(final T...elems) {
        return new BaseMatcher<Iterable<T>>() {
            Iterable<T> iterable;
            List<T> actualElems;
            List<T> expectedElems;
            @SuppressWarnings("unchecked")
            public boolean matches(Object item) {
                iterable = (Iterable<T>) item;
                actualElems = new ArrayList<>();
                for (T elem: iterable) actualElems.add(elem);
                expectedElems = new ArrayList<>(asList(elems));
                return actualElems.equals(expectedElems);
            }

            public void describeTo(Description description) {
                description.appendText(asList(elems).toString());
            }

            public void describeMismatch(Object item, Description description) {
                if (item != iterable) throw new Error();
                description
                        .appendText("expected: ").appendText(expectedElems.toString()).appendText("\n")
                        .appendText("but was:  ").appendText(actualElems.toString());
            }
        };
    }

    public static <T> Matcher<Iterable<T>> isEmpty() {
        return consistsOf(/*nothing*/);
    }
}
