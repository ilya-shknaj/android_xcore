package by.istin.android.xcore.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import by.istin.android.xcore.annotations.dbEntities;
import by.istin.android.xcore.annotations.dbEntity;

public class ReflectUtils {

    private static class XClass {

        private Class<?> clazz;

        private XClass(Class<?> clazz) {
            this.clazz = clazz;
        }

        private final Object lock = new Object();

        private List<XField> listFields;

        private final Map<XClass, Holder> instancesOfInterface = new ConcurrentHashMap<XClass, Holder>();

        public List<XField> getFields() {
            if (listFields == null) {
                synchronized (lock) {
                    if (listFields == null) {
                        listFields = new ArrayList<XField>();
                        Field[] fields = clazz.getFields();
                        for (Field field : fields) {
                            Annotation[] annotations = field.getAnnotations();
                            if (field.getType().equals(String.class) && Modifier.isStatic(field.getModifiers()) && annotations != null && annotations.length != 0) {
                                //we need be sure that all sub entities insert after parent
                                XField xField = new XField(field);
                                if (ReflectUtils.isAnnotationPresent(xField, dbEntity.class) || ReflectUtils.isAnnotationPresent(xField, dbEntities.class)) {
                                    listFields.add(xField);
                                } else {
                                    listFields.add(0, xField);
                                }
                            }
                        }
                    }
                }
            }
            return listFields;
        }

        public <T> T getInstanceInterface(Class<T> interfaceTargetClazz) {
            try {
                XClass xInterfaceClass = getXClass(interfaceTargetClazz);
                Holder<T> result = (Holder<T>) instancesOfInterface.get(xInterfaceClass);
                if (result == null) {
                    synchronized (lock) {
                        result = (Holder<T>) instancesOfInterface.get(xInterfaceClass);
                        if (result == null && !instancesOfInterface.containsKey(xInterfaceClass)) {
                            Class<?> cls = clazz;
                            while (cls != null) {
                                Class<?>[] interfaces = cls.getInterfaces();
                                for (Class<?> i : interfaces) {
                                    if (i.equals(interfaceTargetClazz)) {
                                        T object = (T) clazz.newInstance();
                                        instancesOfInterface.put(xInterfaceClass, new Holder<T>(object));
                                        return object;
                                    }
                                }
                                cls = cls.getSuperclass();
                            }
                        }
                        result = new Holder<T>();
                        instancesOfInterface.put(xInterfaceClass, result);
                    }
                }
                return result.get();
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static class XField {

        private final Field mField;

        private final String mNameOfField;

        private final HashSet<Class<? extends Annotation>> mAnnotations = new HashSet<Class<? extends Annotation>>();

        private final Map<Class<? extends Annotation>, Annotation> mClassAnnotationHashMap = new ConcurrentHashMap<Class<? extends Annotation>, Annotation>();

        XField(Field field) {
            mField = field;

            //init name of field
            mField.setAccessible(true);
            try {
                mNameOfField = (String)mField.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
            mField.setAccessible(false);

            //init annotations
            Annotation[] annotations = mField.getAnnotations();
            if (annotations != null) {
                for (Annotation annotation : annotations) {
                    Class<? extends Annotation> annotationType = annotation.annotationType();
                    mAnnotations.add(annotationType);
                    mClassAnnotationHashMap.put(annotationType, annotation);
                }
            }

        }

        public Field getField() {
            return mField;
        }

        public String getNameOfField() {
            return mNameOfField;
        }

        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return mAnnotations.contains(annotationClass);
        }

        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return (T) mClassAnnotationHashMap.get(annotationClass);
        }
    }

    private static final Map<Class<?>, XClass> sClassesCache = new ConcurrentHashMap<Class<?>, XClass>();

    public static <T extends Annotation> T getAnnotation(XField field, Class<T> annotationClass) {
        return field.getAnnotation(annotationClass);
    }

    public static List<XField> getEntityKeys(Class<?> clazz) {
        return getXClass(clazz).getFields();
    }

    private static XClass getXClass(Class<?> clazz) {
        XClass xClass = sClassesCache.get(clazz);
        if (xClass == null) {
            xClass = new XClass(clazz);
            sClassesCache.put(clazz, xClass);
        }
        return xClass;
    }

    public static boolean isAnnotationPresent(XField field, Class<? extends Annotation> annotationClass) {
        return field.isAnnotationPresent(annotationClass);
    }

	public static String getStaticStringValue(XField field) {
        return field.getNameOfField();
	}

    public static Class<?> classForName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    //TODO move common logic to XClass
	public static <T> T getInstanceInterface(Class<?> clazz, Class<T> interfaceTargetClazz) {
        XClass xClass = getXClass(clazz);
        return xClass.getInstanceInterface(interfaceTargetClazz);
	}

    public static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
