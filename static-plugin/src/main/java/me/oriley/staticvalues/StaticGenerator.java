/*
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.oriley.staticvalues;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static javax.lang.model.element.Modifier.*;

public final class StaticGenerator {

    private static final String PACKAGE_NAME = StaticGenerator.class.getPackage().getName();
    private static final String CLASS_NAME = "S";
    private static final String STATIC_HASH = StaticHasher.getActualHash();

    private static final String XML_TYPE_ITEM = "item";
    private static final String XML_TYPE_BOOL = "bool";
    private static final String XML_TYPE_INTEGER = "integer";
    private static final String XML_TYPE_STRING = "string";

    private static final String XML_ATTR_NAME = "name";
    private static final String XML_ATTR_STATIC = "static";
    private static final String XML_ATTR_TYPE = "type";

    private static final String TRUE = "true";

    private static final Logger log = LoggerFactory.getLogger(StaticGenerator.class.getSimpleName());

    @NonNull
    private final String mBaseOutputDir;

    @NonNull
    private final String mVariantResourceFile;

    @NonNull
    private final String mTaskName;

    private final boolean mDebugLogging;

    @NonNull
    private final List<Resource<String>> mStringResources = new ArrayList<>();

    @NonNull
    private final List<Resource<Boolean>> mBooleanResources = new ArrayList<>();

    @NonNull
    private final List<Resource<Integer>> mIntegerResources = new ArrayList<>();


    public StaticGenerator(@NonNull String baseOutputDir,
                           @NonNull String taskName,
                           @NonNull String variantResourceFile,
                           boolean debugLogging) {
        mBaseOutputDir = baseOutputDir;
        mTaskName = taskName;
        mVariantResourceFile = variantResourceFile;
        mDebugLogging = debugLogging;

        log("StaticGenerator constructed\n" +
                "    Output: " + mBaseOutputDir + "\n" +
                "    Resource File: " + mVariantResourceFile + "\n" +
                "    Package: " + PACKAGE_NAME + "\n" +
                "    Class: " + CLASS_NAME + "\n" +
                "    Logging: " + mDebugLogging);
    }


    public void buildStatic() {
        long startNanos = System.nanoTime();
        File variantFile = new File(mVariantResourceFile);
        if (!variantFile.exists() || !variantFile.isFile()) {
            log("Values file " + variantFile + " not found or invalid");
            return;
        }

        try {
            brewJava().writeTo(new File(mBaseOutputDir));
        } catch (IOException e) {
            logError("Failed to generate java", e, true);
        }

        long lengthMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        log("Time to build was " + lengthMillis + "ms");
    }

    public boolean isStaticHashValid() {
        String staticOutputFile = mBaseOutputDir + '/' + PACKAGE_NAME.replace('.', '/') + "/" + CLASS_NAME + ".java";
        long startNanos = System.nanoTime();
        File file = new File(staticOutputFile);

        boolean returnValue = false;
        if (!file.exists()) {
            log("File " + staticOutputFile + " doesn't exist, hash invalid");
        } else if (!file.isFile()) {
            log("File " + staticOutputFile + " is not a file (?), hash invalid");
        } else {
            returnValue = isFileValid(file, getComments());
        }

        long lengthMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        log("Hash check took " + lengthMillis + "ms, was valid: " + returnValue);
        return returnValue;
    }

    private static void closeQuietly(@Nullable Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignored
            }
        }
    }

    private boolean isFileValid(@NonNull File staticOutputFile, @NonNull String[] comments) {
        if (comments.length <= 0) {
            return false;
        }

        boolean isValid = true;
        try {
            FileReader reader = new FileReader(staticOutputFile);
            BufferedReader input = new BufferedReader(reader);

            for (String comment : comments) {
                String fileLine = input.readLine();
                if (fileLine == null || comment == null || !contains(fileLine, comment)) {
                    log("Aborting, comment: " + comment + ", fileLine: " + fileLine);
                    isValid = false;
                    break;
                } else {
                    log("Line valid, comment: " + comment + ", fileLine: " + fileLine);
                }
            }

            input.close();
            reader.close();
        } catch (IOException e) {
            logError("Error parsing file", e, false);
            isValid = false;
        }

        log("File check result -- isValid ? " + isValid);
        return isValid;
    }

    private void logError(@NonNull String message, @NonNull Throwable error, boolean throwError) {
        log.error("Static: " + message, error);
        if (throwError) {
            throw new IllegalStateException("Static: Fatal Exception");
        }
    }

    private void log(@NonNull String message) {
        if (mDebugLogging) {
            log.warn(mTaskName + ": " + message);
        }
    }

    @NonNull
    private JavaFile brewJava() {

        TypeSpec.Builder builder = TypeSpec.classBuilder(CLASS_NAME)
                .addModifiers(PUBLIC, FINAL);

        try {
            parseValues();
        } catch (XmlPullParserException | IOException e) {
            logError("Failure parsing " + mVariantResourceFile, e, false);
        }

        if (!mBooleanResources.isEmpty()) {
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(XML_TYPE_BOOL).addModifiers(PUBLIC, STATIC, FINAL);
            for (Resource<Boolean> resource : mBooleanResources) {
                classBuilder.addField(createBooleanField(resource));
            }
            builder.addType(classBuilder.build());
        }

        if (!mIntegerResources.isEmpty()) {
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(XML_TYPE_INTEGER).addModifiers(PUBLIC, STATIC, FINAL);
            for (Resource<Integer> resource : mIntegerResources) {
                classBuilder.addField(createIntegerField(resource));
            }
            builder.addType(classBuilder.build());
        }

        if (!mStringResources.isEmpty()) {
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(XML_TYPE_STRING).addModifiers(PUBLIC, STATIC, FINAL);
            for (Resource<String> resource : mStringResources) {
                classBuilder.addField(createStringField(resource));
            }
            builder.addType(classBuilder.build());
        }

        JavaFile.Builder javaBuilder = JavaFile.builder(PACKAGE_NAME, builder.build())
                .indent("    ");

        for (String comment : getComments()) {
            javaBuilder.addFileComment(comment + "\n");
        }

        return javaBuilder.build();
    }

    private void parseValues() throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();

        InputStream inputStream = new FileInputStream(new File(mVariantResourceFile));
        xpp.setInput(new InputStreamReader(inputStream));
        int eventType = xpp.getEventType();

        String currentTag = null;
        String currentItemName = null;
        String currentItemType = null;
        String currentText = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = xpp.getName();

                switch (currentTag) {
                    case XML_TYPE_ITEM:
                        String typeName = xpp.getAttributeValue(null, XML_ATTR_TYPE);
                        if (!isEmpty(typeName)) {
                            currentItemType = typeName;
                        }
                        break;
                    case XML_TYPE_BOOL:
                    case XML_TYPE_INTEGER:
                    case XML_TYPE_STRING:
                        currentItemType = currentTag;
                }

                String staticTag = xpp.getAttributeValue(null, XML_ATTR_STATIC);
                String resourceName = xpp.getAttributeValue(null, XML_ATTR_NAME);
                if (TRUE.equals(staticTag) && !isEmpty(resourceName)) {
                    currentItemName = resourceName;
                } else {
                    currentItemType = null;
                    currentTag = null;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (currentTag != null && currentItemType != null) {
                    currentText = xpp.getText();
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (!isEmpty(currentItemType) && !isEmpty(currentTag) && !isEmpty(currentText)) {
                    switch (currentItemType) {
                        case XML_TYPE_BOOL:
                            boolean boolValue = Boolean.parseBoolean(currentText);
                            mBooleanResources.add(new Resource<>(currentItemName, boolValue));
                            break;
                        case XML_TYPE_INTEGER:
                            try {
                                int value = Integer.parseInt(currentText);
                                mIntegerResources.add(new Resource<>(currentItemName, value));
                            } catch (NumberFormatException nfe) {
                                logError("error parsing integer resource: " + currentText, nfe, false);
                            }
                            break;
                        case XML_TYPE_STRING:
                            mStringResources.add(new Resource<>(currentItemName, currentText));
                            break;
                    }
                }

                currentItemType = null;
                currentTag = null;
                currentText = null;
            }

            eventType = xpp.next();
        }

        closeQuietly(inputStream);
    }

    private static boolean isEmpty(@Nullable String string) {
        return string == null || string.trim().length() <= 0;
    }

    private static boolean contains(@Nullable String str, @Nullable String searchStr) {
        return str != null && searchStr != null && str.contains(searchStr);
    }

    @NonNull
    private String[] getComments() {
        return new String[]{STATIC_HASH, "Package: " + PACKAGE_NAME, "Class: " + CLASS_NAME, "Debug: " + mDebugLogging};
    }

    @NonNull
    private static FieldSpec createBooleanField(@NonNull Resource<Boolean> resource) {
        FieldSpec.Builder builder = FieldSpec.builder(boolean.class, resource.name)
                .addModifiers(PUBLIC, STATIC, FINAL)
                .initializer("$L", resource.value);
        return builder.build();
    }

    @NonNull
    private static FieldSpec createIntegerField(@NonNull Resource<Integer> resource) {
        FieldSpec.Builder builder = FieldSpec.builder(int.class, resource.name)
                .addModifiers(PUBLIC, STATIC, FINAL)
                .initializer("$L", resource.value);
        return builder.build();
    }

    @NonNull
    private static FieldSpec createStringField(@NonNull Resource<String> resource) {
        FieldSpec.Builder builder = FieldSpec.builder(String.class, resource.name)
                .addModifiers(PUBLIC, STATIC, FINAL)
                .initializer("$S", resource.value);
        return builder.build();
    }

    private static final class Resource<T> {

        @NonNull
        final String name;

        @NonNull
        final T value;

        Resource(@NonNull String name, @NonNull T value) {
            this.name = name;
            this.value = value;
        }
    }
}