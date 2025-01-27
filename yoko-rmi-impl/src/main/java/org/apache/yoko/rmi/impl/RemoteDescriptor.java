/**
*
* Licensed to the Apache Software Foundation (ASF) under one or more
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

package org.apache.yoko.rmi.impl;

import org.apache.yoko.util.PrivilegedActions;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.rmi.PortableRemoteObject;

import static java.security.AccessController.doPrivileged;
import static org.apache.yoko.util.PrivilegedActions.getClassLoader;

abstract class RemoteDescriptor extends TypeDescriptor {
    private java.util.Map method_map;

    private java.util.Map refl_method_map;

    private MethodDescriptor[] operations;

    private Class[] remote_interfaces;

    protected List super_descriptors;

    @Override
    protected abstract RemoteInterfaceDescriptor genRemoteInterface();

    private static final Class REMOTE_CLASS = Remote.class;

    private static final Class OBJECT_CLASS = java.lang.Object.class;

    private static final java.lang.Class REMOTE_EXCEPTION = java.rmi.RemoteException.class;

    private volatile String[] ids;
    private String[] genIds() {
        final SortedSet<Class<?>> allRemoteInterfaces = genAllRemoteInterfaces(type);
        final List<String> ids = new ArrayList(allRemoteInterfaces.size());
        for (Class<?> i: allRemoteInterfaces) {
            ids.add(repo.getDescriptor(i).getRepositoryID());
        }
        return ids.toArray(new String[ids.size()]);
    }
    public String[] all_interfaces() {
        if (ids == null) ids = genIds();
        return ids;
    }

    public MethodDescriptor getMethod(String idl_name) {
        if (operations == null) {
            init_methods();
        }

        if (method_map == null) {
            method_map = new HashMap();
            for (int i = 0; i < operations.length; i++) {
                method_map.put(operations[i].getIDLName(), operations[i]);
            }
        }

        return (MethodDescriptor) method_map.get(idl_name);
    }

    void debugMethodMap() {
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("METHOD MAP FOR " + type.getName());

            Iterator it = method_map.keySet().iterator();
            while (it.hasNext()) {
                String idl_name = (String) it.next();
                MethodDescriptor desc = (MethodDescriptor) method_map.get(idl_name);
                logger.finer("IDL " + idl_name + " -> "+ desc.reflected_method);
            }
        }
    }

    public MethodDescriptor getMethod(Method refl_method) {
        if (operations == null) {
            init_methods();
        }

        if (refl_method_map == null) {
            refl_method_map = new HashMap();
            for (int i = 0; i < operations.length; i++) {
                refl_method_map.put(operations[i].getReflectedMethod(), operations[i]);
            }
        }

        return (MethodDescriptor) refl_method_map.get(refl_method);
    }

    RemoteDescriptor(Class type, TypeRepository repository) {
        super(type, repository);
    }

    public MethodDescriptor[] getMethods() {
        if (operations == null) {
            init_methods();
        }
        return operations;
    }

    public synchronized void init_methods() {
        if (operations != null) {
            return;
        }

        doPrivileged(new PrivilegedAction() {
            public Object run() {
                init_methods0();
                return null;
            }
        });
    }

    private void init_methods0() {

        ArrayList method_list = new ArrayList();

        // first step is to build the helpers for any super classes
        Class[] supers = type.getInterfaces();
        super_descriptors = new ArrayList();

        Map all_methods = new HashMap();
        Map lower_case_names = new HashMap();
        for (int i = 0; i < supers.length; i++) {
            Class iface = supers[i];

            if (!REMOTE_CLASS.equals(iface) && !OBJECT_CLASS.equals(iface)
                    && REMOTE_CLASS.isAssignableFrom(iface)
                    && iface.isInterface()) {
                RemoteDescriptor superHelper = (RemoteDescriptor)repo.getDescriptor(iface);

                super_descriptors.add(superHelper);

                MethodDescriptor[] superOps = superHelper.getMethods();
                for (int j = 0; j < superOps.length; j++) {
                    MethodDescriptor op = superOps[j];

                    method_list.add(op);
                    addMethodOverloading(all_methods, op.getReflectedMethod());
                    addMethodCaseSensitive(lower_case_names, op.getReflectedMethod());
                }
            }
        }

        // next, build the method helpers for this class
        Method[] methods = getLocalMethods();

        // register methods
        for (int i = 0; i < methods.length; i++) {
            addMethodOverloading(all_methods, methods[i]);
            addMethodCaseSensitive(lower_case_names, methods[i]);
        }

        Set overloaded_names = new HashSet();
        Iterator it = all_methods.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String mname = (String) entry.getKey();
            Set s = (Set) entry.getValue();
            if (s.size() > 1) {
                overloaded_names.add(mname);
            }
        }

        for (int i = 0; i < methods.length; i++) {
            MethodDescriptor op = new MethodDescriptor(methods[i], repo);

            String mname = op.java_name;

            // is there another method that differs only in case?
            Set same_case_names = (Set) lower_case_names.get(mname.toLowerCase());
            if (same_case_names.size() > 1) {
                op.setCaseSensitive(true);
            }

            // is this method overloaded?
            Set overload_names = (Set) all_methods.get(mname);
            if (overload_names.size() > 1) {
                op.setOverloaded(true);
            }

            op.init();

            method_list.add(op);
        }

        // init method map...
        method_map = new HashMap();
        for (int i = 0; i < method_list.size(); i++) {
            MethodDescriptor desc = (MethodDescriptor) method_list.get(i);
            logger.finer("Adding method " + desc.java_name + " to method map under " + desc.getIDLName());
            method_map.put(desc.getIDLName(), desc);
        }

        //
        // initialize "operations" from the values of the map, such
        // that repeat methods are eliminated.
        //
        operations = (MethodDescriptor[]) method_map.values().toArray(
                new MethodDescriptor[0]);

        debugMethodMap();
    }

    private void addMethodOverloading(Map map, Method m) {
        String mname = m.getName();
        Set entry = (Set) map.get(mname);

        if (entry == null) {
            entry = new HashSet();
            map.put(mname, entry);
        }

        entry.add(createMethodSelector(m));
    }

    Method[] getLocalMethods() {
        ArrayList result = new ArrayList();

        addNonRemoteInterfaceMethods(type, result);

        Method[] out = new Method[result.size()];
        result.toArray(out);
        return out;
    }

    void addNonRemoteInterfaceMethods(Class clz, ArrayList result) {
        Method[] methods;
        try {
            methods = clz.getDeclaredMethods();
        } catch (NoClassDefFoundError e) {
            ClassLoader clzClassLoader = doPrivileged(getClassLoader(clz));
            logger.log(Level.FINER, "cannot find class " + e.getMessage() + " from "
                    + clz.getName() + " (classloader " + clzClassLoader + "): "
                    + e.getMessage(), e);
            throw e;
        }
        for (int j = 0; j < methods.length; j++) {
            // since this is a remote interface, we need to add everything
            result.add(methods[j]);
        }

        Class[] ifaces = clz.getInterfaces();
        for (int i = 0; i < ifaces.length; i++) {
            if (!REMOTE_CLASS.isAssignableFrom(ifaces[i])) {
                addNonRemoteInterfaceMethods(ifaces[i], result);
            }
        }
    }

    boolean isRemoteMethod(Method m) {
        Class[] ex = m.getExceptionTypes();

        for (int i = 0; i < ex.length; i++) {
            if (REMOTE_EXCEPTION.isAssignableFrom(ex[i]))
                return true;
        }

        return false;
    }

    private static String createMethodSelector(java.lang.reflect.Method m) {
        StringBuffer sb = new StringBuffer(m.getName());
        sb.append('(');
        Class[] parameterTypes = m.getParameterTypes();
        for (int n = 0; n < parameterTypes.length; n++) {
            sb.append(parameterTypes[n].getName());
            if (n < parameterTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(')');
        return sb.toString().intern();
    }

    private void addMethodCaseSensitive(Map map, Method m) {
        String mname = m.getName();
        String lowname = mname.toLowerCase();
        Set entry = (Set) map.get(lowname);

        if (entry == null) {
            entry = new HashSet();
            map.put(lowname, entry);
        }

        entry.add(mname);
    }

    static RemoteInterfaceDescriptor genMostSpecificRemoteInterface(Class type, TypeRepository repo) {
        final SortedSet<Class<?>> remoteInterfaces = genAllRemoteInterfaces(type);
        if (remoteInterfaces.isEmpty()) {
            throw new RuntimeException(type.getName() + " has no remote interfaces");
        }
        //first remoteInterface is the most specific
        return repo.getDescriptor(remoteInterfaces.first()).getRemoteInterface();
    }

    private enum InterfaceComparator implements Comparator<Class<?>> {
        INSTANCE;

        public int compare(Class<?> c1, Class<?> c2) {
            if (c1.equals(c2)) return 0;
            if (c1.isAssignableFrom(c2)) return 1;
            if (c2.isAssignableFrom(c1)) return -1;
            //classes are unrelated, so sort on class name
            return c1.getName().compareTo(c2.getName());
        }
    }

    private static SortedSet<Class<?>> genAllRemoteInterfaces(Class<?> type) {
        final SortedSet<Class<?>> remoteInterfaces = new TreeSet(InterfaceComparator.INSTANCE);
        addRemoteInterfacesToSet(type, remoteInterfaces);
        return remoteInterfaces;
    }

    private static void addRemoteInterfacesToSet(Class<?> type, Set<Class<?>> interfaces) {
        if (REMOTE_CLASS.equals(type)) return;
        if (type.isInterface()) interfaces.add(type);
        Class<?> parent = type.getSuperclass();
        if ((parent != null) && !OBJECT_CLASS.equals(parent)) addRemoteInterfacesToSet(parent, interfaces);
        for (Class<?> i: type.getInterfaces()) {
            if (REMOTE_CLASS.isAssignableFrom(i)) addRemoteInterfacesToSet(i, interfaces);
        }
    }

    /** Read an instance of this value from a CDR stream */
    @Override
    public Object read(InputStream in) {
        return PortableRemoteObject.narrow(in.read_Object(),
                type);
    }

    /** Write an instance of this value to a CDR stream */
    @Override
    public void write(OutputStream out, Object val) {
        javax.rmi.CORBA.Util.writeRemoteObject(out, val);
    }

    @Override
    protected final TypeCode genTypeCode() {
        ORB orb = ORB.init();
        return orb.create_interface_tc(getRepositoryID(), type.getName());
    }

    @Override
    void writeMarshalValue(PrintWriter pw, String outName,
            String paramName) {
        pw.print("javax.rmi.CORBA.Util.writeRemoteObject(");
        pw.print(outName);
        pw.print(',');
        pw.print(paramName);
        pw.print(')');
    }

    @Override
    void writeUnmarshalValue(PrintWriter pw, String inName) {
        pw.print('(');
        pw.print(type.getName());
        pw.print(')');
        pw.print(PortableRemoteObject.class.getName());
        pw.print(".narrow(");
        pw.print(inName);
        pw.print('.');
        pw.print("read_Object(),");
        pw.print(type.getName());
        pw.print(".class)");
    }

    static String classNameFromStub(String name) {
        if (name.startsWith("org.omg.stub."))
            name = name.substring("org.omg.stub.".length());

        // strip xx._X_Stub -> xx.X
        int idx = name.lastIndexOf('.');
        if (name.charAt(idx + 1) == '_' && name.endsWith("_Stub")) {
            if (idx == -1) {
                return name.substring(1, name.length() - 5);
            } else {
                return name.substring(0, idx + 1) /* package. */
                        + name.substring(idx + 2, name.length() - 5);
            }
        }

        return null;
    }

    static String stubClassName(Class c) {

        String cname = c.getName();

        String pkgname = null;
        int idx = cname.lastIndexOf('.');
        if (idx == -1) {
            pkgname = "org.omg.stub";
        } else {
            pkgname = "org.omg.stub." + cname.substring(0, idx);
        }

        String cplain = cname.substring(idx + 1);

        return pkgname + "._" + cplain + "_Stub";
    }

    void writeStubClass(PrintWriter pw) {

        Class c = type;
        String cname = c.getName();
        String fullname = stubClassName(c);
        //String stubname = fullname.substring(fullname.lastIndexOf('.') + 1);
        String pkgname = fullname.substring(0, fullname.lastIndexOf('.'));
        String cplain = cname.substring(cname.lastIndexOf('.') + 1);

        pw.println("/** ");
        pw.println(" *  RMI/IIOP stub for " + cname);
        pw.println(" *  Generated using Apache Yoko stub generator.");
        pw.println(" */");

        pw.println("package " + pkgname + ";\n");

        pw.println("public class _" + cplain + "_Stub");
        pw.println("\textends javax.rmi.CORBA.Stub");
        pw.println("\timplements " + cname);
        pw.println("{");

        //
        // construct String[] _ids;
        //
        String[] all_interfaces = all_interfaces();
        pw.println("\tprivate static final String[] _ids = {");
        for (int i = 0; i < all_interfaces.length; i++) {
            pw.println("\t\t\"" + all_interfaces[i] + "\",");
        }
        pw.println("\t};\n");

        pw.println("\tpublic String[] _ids() {");
        pw.println("\t\treturn _ids;");
        pw.println("\t}");

        //
        // now, construct stub methods
        //
        MethodDescriptor[] meths = getMethods();
        for (int i = 0; i < meths.length; i++) {
            meths[i].writeStubMethod(pw);
        }

        pw.println("}");
    }

    String getStubClassName() {
        Class c = type;
        String cname = c.getName();

        String pkgname = null;
        int idx = cname.lastIndexOf('.');
        if (idx == -1) {
            pkgname = "org.omg.stub";
        } else {
            pkgname = "org.omg.stub." + cname.substring(0, idx);
        }

        String cplain = cname.substring(idx + 1);

        return pkgname + "." + "_" + cplain + "_Stub";
    }

    @Override
    void addDependencies(Set classes) {
        Class c = type;

        if (c == Remote.class || classes.contains(c))
            return;

        classes.add(c);

        if (c.getSuperclass() != null) {
            TypeDescriptor desc = repo.getDescriptor(c.getSuperclass());
            desc.addDependencies(classes);
        }

        Class[] ifaces = c.getInterfaces();
        for (int i = 0; i < ifaces.length; i++) {
            TypeDescriptor desc = repo.getDescriptor(ifaces[i]);
            desc.addDependencies(classes);
        }

        MethodDescriptor[] mths = getMethods();
        for (int i = 0; i < mths.length; i++) {
            mths[i].addDependencies(classes);
        }
    }

    @Override
    boolean copyInStub() {
        return false;
    }
}
