[![Release](https://jitpack.io/v/com.github.oriley-me/static-values.svg)](https://jitpack.io/#com.github.oriley-me/static-values)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Build Status](https://travis-ci.org/oriley-me/static-values.svg?branch=master)](https://travis-ci.org/oriley-me/static-values)
[![Dependency Status](https://www.versioneye.com/user/projects/5721e547fcd19a0039f183f4/badge.svg?style=flat)](https://www.versioneye.com/user/projects/5721e547fcd19a0039f183f4)

# Static Values

Easily generate static fields from select resource values, for faster access and guaranteed synchronisation of values.

## Why?

Sometimes you have resources which never change according to configuration, but you need to be able to access via XML
and Java. Traditionally this would require using `context.getResources().getBoolean(R.bool.static_value_pref_default)`
for example. The alternative was to put the values into static final fields manually and copy paste the values (this
is a common paradigm for preference keys for example).

I decided it would be better to give resources a special tag `static="true"`, and generate an `S` class containing a
copy of the resources generated at compile time, for faster access, guaranteed matching values, and simplified code.

The `S` class is syntactically the same as the standard `R` class, except it contains the actual values of the resources,
rather than a resource ID reference.

## Gradle Setup

The first step to using Static Values is to add the plugin to your gradle files.

 * Add JitPack.io repo and `static-values` dependency to your buildscript:


```gradle
buildscript {
    repositories {
        maven { url "https://jitpack.io" }
    }

    dependencies {
        classpath 'com.github.oriley-me:static-values:0.1.0'
    }
}
```


 * Apply the plugin to your application or library project:


```gradle
apply plugin: 'com.android.application' || apply plugin: 'com.android.library'
apply plugin: 'me.oriley.static-plugin'
```


 * That's it! You're static values will now be compile time safe and accessible without using a context or wasting
 time on `Context.getResources()` calls. Let's move on to using them.


## Usage


Now that you've done that, let's setup some resources. Because of the fact these are going to be generated at compile
time and will not change for the life of the application, only values in the default `res/values` folder will be
considered. Values that change due to configuration (i.e. `values-xhdpi`, `values-en` or `values-port` etc) are not
suitable for use as a static value.

To mark a value as being static, add a `static="true"` tag to the XML entry. Only values containing this tag will be
added to the generated source code. In any of your default values files, add the resources you need to have synchronised
with your java code like so:

#### Note: Currently supports `bool`, `integer` and `string` resource types

```xml
    <bool name="pref_test_enable" static="true">false</bool>
    <bool name="pref_default_enable" static="true">true</bool>
    <integer name="pref_test_count" static="true">69</integer>
    <integer name="pref_default_count" static="true">0</integer>
    <string name="pref_key_enable" static="true">pref_enable</string>
    <string name="pref_key_count" static="true">pref_count</string>
```


Now, once you compile, a new class will be generated at `me.oriley.staticvalues.S`, containing the following:


```java
package me.oriley.staticvalues;

import java.lang.String;

public final class S {
    public static final class bool {
        public static final boolean pref_test_enable = false;

        public static final boolean pref_default_enable = true;
    }

    public static final class integer {
        public static final int pref_test_count = 69;

        public static final int pref_default_count = 0;
    }

    public static final class string {
        public static final String pref_key_enable = "pref_enable";

        public static final String pref_key_count = "pref_count";
    }
}
```


This means we can access the values in both XML and Java, without needing to mess around with a `Context` or `Resources`
object. Let's take a look at a sample before and after of a preference fragment and it's XML making use of these values.

This is what the `preferences.xml` would look like in both cases:


```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Note: other attributes omitted for clarity -->

    <CheckBoxPreference android:key="@string/pref_key_enable"
                        android:defaultValue="@bool/pref_default_enable"/>

    <SeekBarPreference android:key="@string/pref_key_count"
                       android:defaultValue="@integer/pref_default_count"/>

</PreferenceScreen>
```


#### PreferenceFragment


This is what it looks like without Static Values:


```java
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        Activity activity = getActivity();
        CheckBoxPreference checkBoxPreference = (CheckBoxPreference) findPreference(activity.getString(R.string.pref_key_enable));
        if (TESTING) {
            checkBoxPreference.setChecked(activity.getResources().getBoolean(R.bool.pref_test_enable));
        }

        SeekBarPreference seekBarPreference = (SeekBarPreference) findPreference(activity.getString(R.string.pref_key_count));
        if (TESTING) {
            seekBarPreference.setProgress(activity.getResources().getInteger(R.integer.pref_test_count));
        }
    }
```


And this is what that same code would look like when taking advantage of Static Values to generate the `S` class
(be sure to import `me.oriley.staticvalues.S` wherever you need to use them):


```java
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        CheckBoxPreference checkBoxPreference = (CheckBoxPreference) findPreference(S.string.pref_key_enable);
        if (TESTING) {
            checkBoxPreference.setChecked(S.bool.pref_test_enable);
        }

        SeekBarPreference seekBarPreference = (SeekBarPreference) findPreference(S.string.pref_key_count);
        if (TESTING) {
            seekBarPreference.setProgress(S.integer.pref_test_count);
        }
    }
```


This will lead to faster execution times, simplified code, and removes the need to resort to manually storing the values
in fields yourself and risking runtime issues because the values are not hard linked to the resources.


## Snapshot Builds


If you would like to check out the latest development version, please substitute all versions for `-SNAPSHOT`.
Keep in mind that it is very likely things could break or be unfinished, so stick the official releases if you want
things to be more predictable.