/*
 * Copyright 2016 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.gwt.client;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMapInput;
import com.google.javascript.jscomp.WarningLevel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * Runner for the GWT-compiled JSCompiler as a single exported method.
 */
public final class GwtRunner implements EntryPoint {

  private static final CompilationLevel DEFAULT_COMPILATION_LEVEL =
      CompilationLevel.SIMPLE_OPTIMIZATIONS;

  private static final Map<String, CompilationLevel> COMPILATION_LEVEL_MAP =
      ImmutableMap.of(
          "WHITESPACE_ONLY",
          CompilationLevel.WHITESPACE_ONLY,
          "SIMPLE",
          CompilationLevel.SIMPLE_OPTIMIZATIONS,
          "SIMPLE_OPTIMIZATIONS",
          CompilationLevel.SIMPLE_OPTIMIZATIONS,
          "ADVANCED",
          CompilationLevel.ADVANCED_OPTIMIZATIONS,
          "ADVANCED_OPTIMIZATIONS",
          CompilationLevel.ADVANCED_OPTIMIZATIONS);

  private static final Map<String, WarningLevel> WARNING_LEVEL_MAP =
      ImmutableMap.of(
          "QUIET",
          WarningLevel.QUIET,
          "DEFAULT",
          WarningLevel.DEFAULT,
          "VERBOSE",
          WarningLevel.VERBOSE);

  private GwtRunner() {}

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private interface Flags {
    @JsProperty boolean getAngularPass();
    @JsProperty boolean getAssumeFunctionWrapper();
    @JsProperty String getCompilationLevel();
    @JsProperty boolean getDartPass();
    @JsProperty boolean getExportLocalPropertyDefinitions();
    @JsProperty boolean getGenerateExports();
    @JsProperty String getLanguageIn();
    @JsProperty String getLanguageOut();
    @JsProperty boolean getChecksOnly();
    @JsProperty boolean getNewTypeInf();
    @JsProperty boolean getPolymerPass();
    @JsProperty boolean getPreserveTypeAnnotations();
    @JsProperty boolean getProcessCommonJSModules();
    @JsProperty String getRenamePrefixNamespace();
    @JsProperty boolean getRewritePolyfills();  // default true
    @JsProperty String getWarningLevel();
    @JsProperty boolean getUseTypesForOptimization();  // default true

    // These flags do not match the Java compiler JAR.
    @JsProperty File[] getJsCode();
    @JsProperty File[] getExterns();
    @JsProperty boolean getCreateSourceMap();  // String in JAR
  }

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private static class File {
    @JsProperty String path;
    @JsProperty String src;
    @JsProperty String sourceMap;
  }

  @JsType(namespace = JsPackage.GLOBAL, name = "Object", isNative = true)
  private static class ModuleOutput {
    @JsProperty String compiledCode;
    @JsProperty String sourceMap;
    @JsProperty JavaScriptObject[] errors;
    @JsProperty JavaScriptObject[] warnings;
  }

  private static native JavaScriptObject createError(String file, String description, String type,
        int lineNo, int charNo) /*-{
    return {file: file, description: description, type: type, lineNo: lineNo, charNo: charNo};
  }-*/;

  /**
   * Convert a list of {@link JSError} instances to a JS array containing plain objects.
   */
  private static JavaScriptObject[] toNativeErrorArray(List<JSError> errors) {
    JavaScriptObject out[] = new JavaScriptObject[errors.size()];
    for (int i = 0; i < errors.size(); ++i) {
      JSError error = errors.get(i);
      DiagnosticType type = error.getType();
      out[i] = createError(error.sourceName, error.description, type != null ? type.key : null,
          error.lineNumber, error.getCharno());
    }
    return out;
  }

  private static void applyDefaultOptions(CompilerOptions options) {
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.DEFAULT.setOptionsForWarningLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT6);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
  }

  private static void applyOptionsFromFlags(CompilerOptions options, Flags flags) {
    CompilationLevel level = DEFAULT_COMPILATION_LEVEL;
    if (flags.getCompilationLevel() != null) {
      level = COMPILATION_LEVEL_MAP.get(flags.getCompilationLevel().toUpperCase());
      if (level == null) {
        throw new RuntimeException(
            "Bad value for compilationLevel: " + flags.getCompilationLevel());
      }
    }
    level.setOptionsForCompilationLevel(options);
    if (flags.getAssumeFunctionWrapper()) {
      level.setWrappedOutputOptimizations(options);
    }
    if (flags.getUseTypesForOptimization()) {
      level.setTypeBasedOptimizationOptions(options);
    }

    WarningLevel warningLevel = WarningLevel.DEFAULT;
    if (flags.getWarningLevel() != null) {
      warningLevel = WARNING_LEVEL_MAP.get(flags.getWarningLevel().toUpperCase());
      if (warningLevel == null) {
        throw new RuntimeException(
            "Bad value for compilationLevel: " + flags.getCompilationLevel());
      }
    }
    warningLevel.setOptionsForWarningLevel(options);

    if (flags.getLanguageIn() != null) {
      LanguageMode languageIn = LanguageMode.fromString(flags.getLanguageIn());
      if (languageIn != null) {
        options.setLanguageIn(languageIn);
      }
    }

    if (flags.getLanguageOut() != null) {
      LanguageMode languageOut = LanguageMode.fromString(flags.getLanguageOut());
      if (languageOut != null) {
        options.setLanguageOut(languageOut);
      }
    }

    if (flags.getCreateSourceMap()) {
      options.setSourceMapOutputPath("%output%");
    }

    options.setAngularPass(flags.getAngularPass());
    options.setChecksOnly(flags.getChecksOnly());
    options.setDartPass(flags.getDartPass());
    options.setExportLocalPropertyDefinitions(flags.getExportLocalPropertyDefinitions());
    options.setGenerateExports(flags.getGenerateExports());
    options.setNewTypeInference(flags.getNewTypeInf());
    options.setPolymerPass(flags.getPolymerPass());
    options.setPreserveTypeAnnotations(flags.getPreserveTypeAnnotations());
    options.setProcessCommonJSModules(flags.getProcessCommonJSModules());
    options.setRenamePrefixNamespace(flags.getRenamePrefixNamespace());
    options.setRewritePolyfills(flags.getRewritePolyfills());
  }

  private static void disableUnsupportedOptions(CompilerOptions options) {
    options.getDependencyOptions().setDependencySorting(false);
  }

  private static List<SourceFile> fromFileArray(File[] src, String unknownPrefix) {
    List<SourceFile> out = new ArrayList<>();
    if (src != null) {
      for (int i = 0; i < src.length; ++i) {
        File file = src[i];
        String path = file.path;
        if (path == null) {
          path = unknownPrefix + i;
        }
        out.add(SourceFile.fromCode(path, nullToEmpty(file.src)));
      }
    }
    return ImmutableList.copyOf(out);
  }

  private static ImmutableMap<String, SourceMapInput> buildSourceMaps(
      File[] src, String unknownPrefix) {
    ImmutableMap.Builder<String, SourceMapInput> inputSourceMaps = new ImmutableMap.Builder<>();
    if (src != null) {
      for (int i = 0; i < src.length; ++i) {
        File file = src[i];
        if (isNullOrEmpty(file.sourceMap)) {
          continue;
        }
        String path = file.path;
        if (path == null) {
          path = unknownPrefix + i;
        }
        path += ".map";
        SourceFile sf = SourceFile.fromCode(path, file.sourceMap);
        inputSourceMaps.put(path, new SourceMapInput(sf));
      }
    }
    return inputSourceMaps.build();
  }

  /**
   * Public compiler call. Exposed in {@link #exportCompile}.
   */
  public static ModuleOutput compile(Flags flags) {
    List<SourceFile> externs = fromFileArray(flags.getExterns(), "Extern_");
    List<SourceFile> jsCode = fromFileArray(flags.getJsCode(), "Input_");
    ImmutableMap<String, SourceMapInput> sourceMaps = buildSourceMaps(flags.getJsCode(), "Input_");

    CompilerOptions options = new CompilerOptions();
    applyDefaultOptions(options);
    applyOptionsFromFlags(options, flags);
    options.setInputSourceMaps(sourceMaps);
    disableUnsupportedOptions(options);

    NodeErrorManager errorManager = new NodeErrorManager();
    Compiler compiler = new Compiler();
    compiler.setErrorManager(errorManager);
    compiler.compile(externs, jsCode, options);

    ModuleOutput output = new ModuleOutput();
    output.compiledCode = compiler.toSource();
    output.errors = toNativeErrorArray(errorManager.errors);
    output.warnings = toNativeErrorArray(errorManager.warnings);

    if (flags.getCreateSourceMap()) {
      StringBuilder b = new StringBuilder();
      try {
        compiler.getSourceMap().appendTo(b, "IGNORED");
      } catch (IOException e) {
        // ignore
      }
      output.sourceMap = b.toString();
    }

    return output;
  }

  /**
   * Exports the {@link #compile} method via JSNI.
   *
   * This will be placed on {@code module.exports} or {@code this}.
   */
  public native void exportCompile() /*-{
    var fn = $entry(@com.google.javascript.jscomp.gwt.client.GwtRunner::compile(*));
    if (typeof module !== 'undefined' && module.exports) {
      module.exports = fn;
    } else {
      this.compile = fn;
    }
  }-*/;

  @Override
  public void onModuleLoad() {
    exportCompile();
  }

  /**
   * Custom {@link BasicErrorManager} to record {@link JSError} instances.
   */
  private static class NodeErrorManager extends BasicErrorManager {
    final List<JSError> errors = new ArrayList<>();
    final List<JSError> warnings = new ArrayList<>();

    @Override
    public void println(CheckLevel level, JSError error) {
      if (level == CheckLevel.ERROR) {
        errors.add(error);
      } else if (level == CheckLevel.WARNING) {
        warnings.add(error);
      }
    }

    @Override
    public void printSummary() {}
  }
}
