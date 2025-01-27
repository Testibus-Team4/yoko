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
package org.apache.yoko.rmi.util.stub;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Synthetic;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ARETURN;
import org.apache.bcel.generic.ASTORE;
import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DRETURN;
import org.apache.bcel.generic.FRETURN;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.IRETURN;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LRETURN;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.NEW;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.Type;
import org.apache.yoko.rmi.impl.RMIStub;
import org.apache.yoko.rmispec.util.DelegateType;
import org.omg.CORBA.INITIALIZE;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isStatic;
import static java.security.AccessController.doPrivileged;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.IntStream.range;
import static org.apache.bcel.Constants.ACC_FINAL;
import static org.apache.bcel.Constants.ACC_PRIVATE;
import static org.apache.bcel.Constants.ACC_PUBLIC;
import static org.apache.bcel.Constants.ACC_STATIC;
import static org.apache.bcel.Constants.T_ARRAY;
import static org.apache.bcel.Constants.T_BOOLEAN;
import static org.apache.bcel.Constants.T_BYTE;
import static org.apache.bcel.Constants.T_CHAR;
import static org.apache.bcel.Constants.T_DOUBLE;
import static org.apache.bcel.Constants.T_FLOAT;
import static org.apache.bcel.Constants.T_INT;
import static org.apache.bcel.Constants.T_LONG;
import static org.apache.bcel.Constants.T_OBJECT;
import static org.apache.bcel.Constants.T_SHORT;
import static org.apache.bcel.generic.Type.getArgumentTypes;
import static org.apache.bcel.generic.Type.getReturnType;
import static org.apache.yoko.rmi.util.stub.BCELClassBuilder.StubInitializerHolder.RMI_STUB_INITIALIZER;
import static org.apache.yoko.util.Exceptions.as;
import static org.apache.yoko.util.PrivilegedActions.action;

class BCELClassBuilder {
    private static final Logger logger = Logger.getLogger(BCELClassBuilder.class.getName());

    static <S> Class<S> makeStub(ClassLoader loader, Class<?> type, MethodRef[] methods, Object[] data, MethodRef handlerMethodRef, String className) {
        String superClassName = RMIStub.class.getName();
        String[] interfaceNames = { type.getName(), Stub.class.getName() };

        ClassGen newStubClass = new ClassGen(className, superClassName, "generated", ACC_PUBLIC | ACC_FINAL, interfaceNames);
        ConstantPoolGen cp = newStubClass.getConstantPool();

        Class<?>[] paramTypes = requireNonNull(handlerMethodRef, "handler method is null").getParameterTypes();
        if (paramTypes.length != 3) throw new IllegalArgumentException("handler method must have three arguments");
        if (!paramTypes[0].isAssignableFrom(RMIStub.class)) throw new IllegalArgumentException("Handler's 1st argument must be super-type for " + RMIStub.class);

        Type typeOfDataFields = translate(paramTypes[1]);
        if (Object[].class != paramTypes[2]) throw new IllegalArgumentException("Handler's 3rd argument must be Object[]");

        // Construct field for the handler reference
        Class<?> handlerClass = handlerMethodRef.getDeclaringClass();
        FieldGen handlerFieldGen = new FieldGen(ACC_PRIVATE | ACC_FINAL, translate(handlerClass), "__handler", cp);
        newStubClass.addField(handlerFieldGen.getField());

        // Construct the method that gets the stub handler.
        generateHandlerGetter(newStubClass, handlerFieldGen);

        // construct the field that holds the initializer
        FieldGen initializerFieldGen = new FieldGen(ACC_PRIVATE | ACC_STATIC, translate(StubInitializer.class), "__initializer", cp);
        newStubClass.addField(initializerFieldGen.getField());

        // Emit constructor
        emitInitializerConstructor(newStubClass, handlerFieldGen, initializerFieldGen);

        // Construct data fields
        FieldGen[] dataFieldGens = new FieldGen[methods.length];
        range(0, methods.length).forEach(i -> {
            dataFieldGens[i] = new FieldGen(ACC_PRIVATE | ACC_STATIC, typeOfDataFields, "__method$" + i, cp);
            newStubClass.addField(dataFieldGens[i].getField());
        });

        // Construct method stubs
        range(0, methods.length).forEach(i -> generate(newStubClass, methods[i], dataFieldGens[i], handlerFieldGen, handlerMethodRef));

        JavaClass javaClass = newStubClass.getJavaClass();
        byte[] classData = javaClass.getBytes();

        return doPrivileged(action(() -> {
            // This is a large doPrivileged block,
            // but almost every action in here requires privilege to be asserted.
            if (Boolean.getBoolean("org.apache.yoko.rmi.util.stub.debug")) { // privileged
                File out = new File(className + ".class");
                try {
                    javaClass.dump(out); //privileged
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "", ex);
                }
            }

            Class<S> proxyClass = Util.defineClass(loader, className, classData); //privileged

            // initialize the static data fields
            range(0, data.length).forEach(i -> {
                try {
                    Field f = proxyClass.getDeclaredField(dataFieldGens[i].getName()); // privileged
                    f.setAccessible(true); // privileged
                    f.set(null, data[i]); // privileged
                    f.setAccessible(false); // privileged
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    logger.log(Level.WARNING, "cannot find/access field " + dataFieldGens[i].getName()
                            + " for stub class " + className + " extends: " + superClassName
                            + " implements: " + String.join(", ", interfaceNames), e);
                    throw new Error("internal error!", e);
                }
            });

            // set the initializer
            try {
                Field f = proxyClass.getDeclaredField( "__initializer"); // privileged
                f.setAccessible(true); // privileged
                f.set(null, RMI_STUB_INITIALIZER); // privileged
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new Error("internal error!", ex);
            }

            return proxyClass;
        }));
    }

    private static final Map<Class<?>, Type> KNOWN_TYPE_MAP = unmodifiableMap(new HashMap<Class<?>, Type>() {{
       put(int.class, Type.INT);
       put(boolean.class, Type.BOOLEAN);
       put(short.class, Type.SHORT);
       put(byte.class, Type.BYTE);
       put(long.class, Type.LONG);
       put(double.class, Type.DOUBLE);
       put(float.class, Type.FLOAT);
       put(char.class, Type.CHAR);
       put(void.class, Type.VOID);
       put(Object.class, Type.OBJECT);
       put(Class.class, Type.CLASS);
       put(String.class, Type.STRING);
       put(StringBuffer.class, Type.STRINGBUFFER);
       put(Throwable.class, Type.THROWABLE);
    }});

    private static Type translate(Class<?> clazz) {
        Type result = KNOWN_TYPE_MAP.get(clazz);
        if (result != null) return result;
        if (clazz.isPrimitive()) throw new InternalError("Unknown primitive type: " + clazz);
        return clazz.isArray() ?
                new ArrayType(translate(clazz.getComponentType()), 1) :
                new ObjectType(clazz.getName());
    }

    private static Type[] translate(Class<?>[] clazz) {
        return Stream.of(clazz)
                .map(BCELClassBuilder::translate)
                .toArray(Type[]::new);
    }

    /**
     * Collect the set of method objects that are would be abstract in a
     * subclass of <code>super_class</code>, implementing
     * <code>interfaces</code>.
     */
    public static MethodRef[] collectMethods(Class<?> super_class, Class<?> type) {
        HashMap<String, MethodRef> methods = new HashMap<>();

        collectAbstractMethods(methods, type);
        collectAbstractMethods(methods, super_class);
        removeImplementedMethods(methods, super_class);

        //noinspection SimplifyStreamApiCallChains
        return methods.values().stream().toArray(MethodRef[]::new);
    }

    /**
     * Collect all methods to be generated. We'll only collect each method once;
     * so multiple redeclations will be eliminetd.
     */
    private static void collectAbstractMethods(Map<String, MethodRef> methods, Class<?> type) {
        if (type == Object.class || type == null) return;

        for (Class<?> if_type : type.getInterfaces()) collectAbstractMethods(methods, if_type);

        collectAbstractMethods(methods, type.getSuperclass());

        Predicate<Method> isInterfaceOrAbstractMethod = type.isInterface() ?
                m -> true :
                m -> isAbstract(m.getModifiers());

        Arrays.stream(type.getDeclaredMethods())
                .filter(isInterfaceOrAbstractMethod)
                .map(MethodRef::new)
                .forEach(m -> methods.putIfAbsent(m.getKey(), m));
    }

    /**
     * This is used in the second phase of collect, to remove methods that have
     * been collected in collectAbstractMethods.
     */
    private static void removeImplementedMethods(Map<String, ?> methods, Class<?> type) {
        if (type == Object.class || type == null) return;
        removeImplementedMethods(methods, type.getSuperclass());
        Arrays.stream(type.getDeclaredMethods())
                .filter(m -> !isAbstract(m.getModifiers()))
                .map(MethodRef::getKey)
                .forEach(methods::remove);
    }

    private static final MethodRef getStubHandlerRef;

    static {
        try {
            getStubHandlerRef = new MethodRef(StubInitializer.class.getDeclaredMethod("getStubHandler"));
        } catch (NoSuchMethodException ex) {
            throw new Error(ex.getMessage(), ex);
        }
    }

    //
    // Constructor for a stub with an initializer
    //
    private static void emitInitializerConstructor(ClassGen stubClass, FieldGen handlerField, FieldGen initializerField) {
        String stubClassName = stubClass.getClassName();
        ConstantPoolGen cp = stubClass.getConstantPool();
        InstructionList il = new InstructionList();

        MethodGen mg = new MethodGen(ACC_PUBLIC, Type.VOID, Type.NO_ARGS, null, "<init>", stubClassName, il, cp);

        InstructionFactory fac = new InstructionFactory(stubClass, cp);

        // call super-constructor
        il.append(InstructionFactory.createThis());
        il.append(fac.createInvoke(stubClass.getSuperclassName(), "<init>", Type.VOID, Type.NO_ARGS, Constants.INVOKESPECIAL));

        // push "this"
        il.append(InstructionFactory.createThis());

        // get static initializer
        il.append(fac.createGetStatic(stubClassName, initializerField.getName(), initializerField.getType()));

        emitInvoke(il, fac, getStubHandlerRef);

        // checkCast
        il.append(fac.createCast(Type.OBJECT, handlerField.getType()));

        // put handlerField
        il.append(new PUTFIELD(cp.addFieldref(stubClassName, handlerField.getName(), handlerField.getSignature())));

        // return
        il.append(InstructionConstants.RETURN);

        // compute stack and locals...
        mg.setMaxStack();
        mg.setMaxLocals();

        stubClass.addMethod(mg.getMethod());
    }

    private static void generateHandlerGetter(ClassGen clazz, FieldGen handlerField) {
        Method[] stub_methods = Stub.class.getDeclaredMethods();
        if (stub_methods.length != 1) throw new IllegalStateException("" + Stub.class + " has wrong # methods");

        String handlerGetName = stub_methods[0].getName();

        ConstantPoolGen cp = clazz.getConstantPool();
        InstructionList il = new InstructionList();
        InstructionFactory fac = new InstructionFactory(clazz, cp);

        Type methodReturnType = translate(Object.class);
        Type[] methodArgTypes = new Type[0];

        MethodGen mg = new MethodGen(ACC_FINAL | ACC_PUBLIC, methodReturnType,
                methodArgTypes, null, // arg names
                handlerGetName, clazz.getClassName(), il, cp);

        mg.addAttribute(new Synthetic(cp.addUtf8("Synthetic"), 0, null, cp.getConstantPool()));

        // construct method body
        il.append(InstructionFactory.createThis());
        il.append(fac.createGetField(clazz.getClassName(), handlerField.getName(), handlerField.getType()));
        emitReturn(il, methodReturnType);

        // finish up...
        mg.setMaxStack();
        mg.setMaxLocals();
        clazz.addMethod(mg.getMethod());
    }

    private static void generate(ClassGen clazz, MethodRef method, FieldGen dataField, FieldGen handlerField, MethodRef handlerMethodRef) {
        ConstantPoolGen cp;
        InstructionList il;

        cp = clazz.getConstantPool();
        il = new InstructionList();

        InstructionFactory fac = new InstructionFactory(clazz, cp);

        Type methodReturnType = translate(method.getReturnType());
        Type[] methodArgTypes = translate(method.getParameterTypes());

        MethodGen mg = new MethodGen(ACC_FINAL | ACC_PUBLIC, methodReturnType, methodArgTypes, null, method.getName(), clazz.getClassName(), il, cp);
        mg.addAttribute(new Synthetic(cp.addUtf8("Synthetic"), 0, null, cp.getConstantPool()));

        Arrays.stream(method.getExceptionTypes())
                .map(Class::getName)
                .forEach(mg::addException);

        // BODY
        il.append(InstructionFactory.createThis());
        il.append(fac.createGetField(clazz.getClassName(), handlerField.getName(), handlerField.getType()));

        // push "this" as invoke's first argument
        il.append(InstructionFactory.createThis());

        // load data value
        if (dataField.isStatic()) {
            il.append(fac.createGetStatic(clazz.getClassName(), dataField.getName(), dataField.getType()));
        } else {
            il.append(InstructionFactory.createThis());
            il.append(fac.createGetField(clazz.getClassName(), dataField.getName(), dataField.getType()));
        }

        il.append(new PUSH(cp, methodArgTypes.length));
        il.append(fac.createNewArray(Type.OBJECT, (short) 1));

        for (int i = 0, index = 1; i < methodArgTypes.length; i++) {
            // dup array ref
            il.append(InstructionConstants.DUP);
            // push index
            il.append(new PUSH(cp, i));
            // transform parameter
            il.append(InstructionFactory.createLoad(methodArgTypes[i], index));
            emitCoerceToObject(il, fac, methodArgTypes[i]);
            // and store into array
            il.append(InstructionFactory.createArrayStore(Type.OBJECT));
            index += methodArgTypes[i].getSize();
        }

        // invoke handler
        InstructionHandle tryStart = emitInvoke(il, fac, handlerMethodRef);

        // convert to primitive type
        emitCoerceFromObject(il, fac, methodReturnType);

        // and return
        InstructionHandle tryEnd = emitReturn(il, methodReturnType);

        // catch...
        InstructionHandle rethrowLocation = il.append(new ATHROW());

        Class<?>[] exceptions = method.getExceptionTypes();
        boolean handle_throwable_exception = true;
        boolean handle_runtime_exception = true;
        if (exceptions != null) {
            for (Class<?> ex: exceptions) {
                if (ex == Throwable.class) handle_throwable_exception = false;
                if (ex == RuntimeException.class || ex == Exception.class) handle_runtime_exception = false;
                mg.addExceptionHandler(tryStart, tryEnd, rethrowLocation, (ObjectType) translate(ex));
            }
        }

        // A RuntimeException should not cause an UndeclaredThrowableException, so we catch and re-throw it before throwable.
        if (handle_throwable_exception && handle_runtime_exception) mg.addExceptionHandler(tryStart, tryEnd, rethrowLocation, new ObjectType("java.lang.RuntimeException"));


        // If anything else is thrown, it is wrapped in an UndeclaredThrowable
        if (handle_throwable_exception) {
            InstructionHandle handlerStart = il.append(new ASTORE(1));
            il.append(new NEW(cp.addClass("java.lang.reflect.UndeclaredThrowableException")));
            il.append(InstructionConstants.DUP);
            il.append(new ALOAD(1));
            il.append(new INVOKESPECIAL(cp.addMethodref("java.lang.reflect.UndeclaredThrowableException", "<init>", "(Ljava/lang/Throwable;)V")));
            il.append(new ATHROW());
            mg.addExceptionHandler(tryStart, tryEnd, handlerStart, new ObjectType("java.lang.Throwable"));
        }

        // DONE
        mg.setMaxStack();
        mg.setMaxLocals();
        clazz.addMethod(mg.getMethod());
    }

    private static InstructionHandle emitReturn(InstructionList il, Type type) {
        return PrimitiveTypeEmitters.get(type)
                .map(pte -> pte.emitReturn.apply(il))
                .orElse(il.append(new ARETURN()));
    }

    private static void emitCoerceToObject(InstructionList il, InstructionFactory fac, Type type) {
        PrimitiveTypeEmitters.get(type)
                .map(pte -> pte.emitBox.apply(il,fac));
    }

    private static void emitCoerceFromObject(InstructionList il, InstructionFactory fac, Type type) {
        PrimitiveTypeEmitters.get(type)
                .map(pte -> pte.emitUnbox)
                .orElse(emitCoerceToTypeFromObject(type))
                .apply(il, fac);
    }

    private static BiFunction<InstructionList, InstructionFactory, InstructionHandle> emitCoerceToTypeFromObject(Type type) {
        return (il, fac) -> {
            switch(type.getType()) {
            case T_OBJECT:
            case T_ARRAY:
                return il.append(fac.createCast(Type.OBJECT, type));
            default:
                throw new Error();
            }
        };
    }

    enum PrimitiveTypeEmitters {
        BOOLEAN_EMITTERS(boolean.class, Boolean.class),
        CHAR_EMITTERS(char.class, Character.class),
        BYTE_EMITTERS(byte.class, Byte.class),
        SHORT_EMITTERS(short.class, Short.class),
        INT_EMITTERS(int.class, Integer.class),
        LONG_EMITTERS(long.class, Long.class),
        FLOAT_EMITTERS(float.class, Float.class),
        DOUBLE_EMITTERS(double.class, Double.class),
        VOID_EMITTERS(Type.VOID,
                (il, fac) -> il.append(InstructionConstants.POP),
                (il, fac) -> il.append(InstructionConstants.ACONST_NULL),
                (il) -> il.append(InstructionConstants.RETURN));

        final Type type;
        final BiFunction<InstructionList, InstructionFactory, InstructionHandle> emitUnbox;
        final BiFunction<InstructionList, InstructionFactory, InstructionHandle> emitBox;
        final Function<InstructionList, InstructionHandle> emitReturn;

        private static final Map<Type, PrimitiveTypeEmitters> EMITTERS = unmodifiableMap(Arrays.stream(values())
                    .collect(Collectors.toMap(pte -> pte.type, pte -> pte)));

        static Optional<PrimitiveTypeEmitters> get(Type type) {
            return Optional.ofNullable(EMITTERS.get(type));
        }

        PrimitiveTypeEmitters(Class<?> primitiveClass, Class<?> wrapperClass) {
            this(Type.getType(primitiveClass), wrapperClass, primitiveClass.getName() + "Value");
        }

        PrimitiveTypeEmitters(Type type, Class<?> wrapperClass, String unboxMethodName) {
            this(type, genEmitUnbox(wrapperClass, unboxMethodName), genEmitBox(type, wrapperClass), genEmitReturn(type));
        }

        PrimitiveTypeEmitters(Type type, BiFunction<InstructionList, InstructionFactory, InstructionHandle> emitUnbox,
                              BiFunction<InstructionList, InstructionFactory, InstructionHandle> emitBox,
                              Function<InstructionList, InstructionHandle> emitReturn) {
            this.type = type;
            this.emitUnbox = emitUnbox;
            this.emitBox = emitBox;
            this.emitReturn = emitReturn;
        }

        private static Function<InstructionList, InstructionHandle> genEmitReturn(Type type) {
            switch (type.getType()) {
            case T_BOOLEAN:
            case T_CHAR:
            case T_BYTE:
            case T_SHORT:
            case T_INT: return (il) -> il.append(new IRETURN());
            case T_LONG: return (il) -> il.append(new LRETURN());
            case T_FLOAT: return (il) -> il.append(new FRETURN());
            case T_DOUBLE: return (il) -> il.append(new DRETURN());
            default: throw new InternalError();
            }
        }

        private static BiFunction<InstructionList, InstructionFactory, InstructionHandle> genEmitBox(Type type, Class<?> wrapperClass) {
            String wrapperClassName = wrapperClass.getName();
            Type[] argTypes = { type };
            ObjectType newObjectType = new ObjectType(wrapperClassName);
            switch (type.getType()) {
            case T_BOOLEAN:
            case T_CHAR:
            case T_BYTE:
            case T_SHORT:
            case T_INT:
            case T_FLOAT:
                return (il,fac) -> {
                    // float
                    il.append(fac.createNew(newObjectType));
                    // float Float
                    il.append(InstructionConstants.DUP_X1);
                    // Float float Float
                    il.append(InstructionConstants.SWAP);
                    // Float Float float
                    return il.append(fac.createInvoke(wrapperClassName, "<init>", Type.VOID, argTypes, Constants.INVOKESPECIAL));
                };
            case T_DOUBLE:
            case T_LONG:
                return (il,fac) -> {
                    // double/2
                    il.append(fac.createNew(newObjectType));
                    // double/2 Double
                    il.append(InstructionConstants.DUP_X2);
                    // Double double/2 Double
                    il.append(InstructionConstants.DUP_X2);
                    // Double Double double/2 Double
                    il.append(InstructionConstants.POP);
                    // Double Double double/2
                    return il.append(fac.createInvoke(wrapperClassName, "<init>", Type.VOID, argTypes, Constants.INVOKESPECIAL));
                };
            default:
                throw new InternalError();
            }
        }


        private static BiFunction<InstructionList, InstructionFactory, InstructionHandle> genEmitUnbox(Class<?> wrapperClass, String unwrapMethodName) {
            ObjectType objectType = new ObjectType(wrapperClass.getName());
            try {
                MethodRef unwrapMethodRef = new MethodRef(wrapperClass.getDeclaredMethod(unwrapMethodName));
                return (il,fac) -> {
                    il.append(fac.createCast(Type.OBJECT, objectType));
                    return emitInvoke(il, fac, unwrapMethodRef);
                };
            } catch (NoSuchMethodException e) {
                throw new Error("unwrap method not found for " + wrapperClass, e);
            }
        }
    }

    private static InstructionHandle emitInvoke(InstructionList il, InstructionFactory fac, MethodRef method) {
        String signature = method.getSignature();
        Type[] args = getArgumentTypes(signature);
        Type ret = getReturnType(signature);
        String mname = method.getName();
        String cname = method.getDeclaringClass().getName();

        final short kind;
        if (method.getDeclaringClass().isInterface()) kind = Constants.INVOKEINTERFACE;
        else if (isStatic(method.getModifiers())) kind = Constants.INVOKESTATIC;
        else if (method.getName().charAt(0) == '<') kind = Constants.INVOKESPECIAL;
        else kind = Constants.INVOKEVIRTUAL;

        return il.append(fac.createInvoke(cname, mname, ret, args, kind));
    }

    enum StubInitializerHolder {
        ;
        static final StubInitializer RMI_STUB_INITIALIZER;

        static {
            try {
                Constructor<? extends StubInitializer> constructor = doPrivEx(DelegateType.STUB_INIT.getConstructorAction());
                RMI_STUB_INITIALIZER = constructor.newInstance();
            } catch (Exception e) {
                throw as(INITIALIZE::new, e, "Can not create RMIStubInitializer");
            }
        }
        private static <T> T doPrivEx(PrivilegedExceptionAction<T> action) throws PrivilegedActionException { return doPrivileged(action); }
    }
}
