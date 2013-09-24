/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.report.html;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.pitest.classinfo.ClassInfo;
import org.pitest.coverage.CoverageDatabase;
import org.pitest.functional.F;
import org.pitest.functional.FCollection;
import org.pitest.functional.Option;
import org.pitest.mutationtest.MutationMetaData;
import org.pitest.mutationtest.MutationResultList;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.SourceLocator;
import org.pitest.mutationtest.report.MutationTestSummaryData;
import org.pitest.mutationtest.report.MutationTotals;
import org.pitest.util.FileUtil;
import org.pitest.util.IsolationUtils;
import org.pitest.util.Log;
import org.pitest.util.ResultOutputStrategy;

public class MutationHtmlReportListener implements MutationResultListener {

  private final ResultOutputStrategy      outputStrategy;

  private final Collection<SourceLocator> sourceRoots        = new HashSet<SourceLocator>();

  private final PackageSummaryMap         packageSummaryData = new PackageSummaryMap();
  private final CoverageDatabase          coverage;

  public MutationHtmlReportListener(final CoverageDatabase coverage,
      final ResultOutputStrategy outputStrategy,
      final SourceLocator... locators) {
    this.coverage = coverage;
    this.outputStrategy = outputStrategy;
    this.sourceRoots.addAll(Arrays.asList(locators));
  }

  private void generateAnnotatedSourceFile(
      final MutationTestSummaryData mutationMetaData) {
    try {

      final String css = FileUtil.readToString(IsolationUtils
          .getContextClassLoader().getResourceAsStream(
              "templates/mutation/style.css"));

      final String fileName = mutationMetaData.getPackageName()
          + File.separator + mutationMetaData.getFileName() + ".html";

      final Writer writer = this.outputStrategy.createWriterForFile(fileName);

      final StringTemplateGroup group = new StringTemplateGroup("mutation_test");
      final StringTemplate st = group
          .getInstanceOf("templates/mutation/mutation_report");
      st.setAttribute("css", css);

      st.setAttribute("tests", mutationMetaData.getTests());

      st.setAttribute("mutators", mutationMetaData.getMutators());

      final SourceFile sourceFile = createAnnotatedSourceFile(mutationMetaData);

      st.setAttribute("sourceFile", sourceFile);
      st.setAttribute("mutatedClasses", mutationMetaData.getMutatedClasses());

      writer.write(st.toString());
      writer.close();

    } catch (final IOException ex) {
      Log.getLogger().log(Level.WARNING, "Error while writing report", ex);
    }
  }

  private PackageSummaryData collectPackageSummaries(
      final MutationMetaData mutationMetaData) {
    final String packageName = mutationMetaData.getPackageName();
    return this.packageSummaryData.update(packageName,
        mutationMetaData.createSummaryData(this.coverage));
  }

  private SourceFile createAnnotatedSourceFile(
      final MutationTestSummaryData mutationMetaData) throws IOException {

    final String fileName = mutationMetaData.getFileName();

    final MutationResultList mutationsForThisFile = mutationMetaData
        .getResults();

    final List<Line> lines = createAnnotatedSourceCodeLines(fileName,
        mutationsForThisFile);

    return new SourceFile(fileName, lines,
        mutationsForThisFile.groupMutationsByLine());
  }

  private List<Line> createAnnotatedSourceCodeLines(final String sourceFile,
      final MutationResultList mutationsForThisFile) throws IOException {
    final Collection<ClassInfo> classes = this.coverage
        .getClassesForFile(sourceFile);
    final Option<Reader> reader = findSourceFile(classInfoToNames(classes),
        sourceFile);
    if (reader.hasSome()) {
      final AnnotatedLineFactory alf = new AnnotatedLineFactory(
          mutationsForThisFile, this.coverage, classes);
      return alf.convert(reader.value());
    }
    return Collections.emptyList();
  }

  private Collection<String> classInfoToNames(
      final Collection<ClassInfo> classes) {
    return FCollection.map(classes, classInfoToJavaName());
  }

  private F<ClassInfo, String> classInfoToJavaName() {
    return new F<ClassInfo, String>() {

      public String apply(final ClassInfo a) {
        return a.getName().asJavaName();
      }

    };
  }

  private Option<Reader> findSourceFile(final Collection<String> classes,
      final String fileName) {
    for (final SourceLocator each : this.sourceRoots) {
      final Option<Reader> maybe = each.locate(classes, fileName);
      if (maybe.hasSome()) {
        return maybe;
      }
    }
    return Option.none();
  }

  public void onRunEnd() {
    createIndexPages();
  }

  private void createIndexPages() {

    final StringTemplateGroup group = new StringTemplateGroup("mutation_test");
    final StringTemplate st = group
        .getInstanceOf("templates/mutation/mutation_package_index");

    final Writer writer = this.outputStrategy.createWriterForFile("index.html");
    final MutationTotals totals = new MutationTotals();

    final List<PackageSummaryData> psd = new ArrayList<PackageSummaryData>(
        this.packageSummaryData.values());
    Collections.sort(psd);
    for (final PackageSummaryData psData : psd) {
      totals.add(psData.getTotals());
      createPackageIndexPage(psData);
    }

    st.setAttribute("totals", totals);
    st.setAttribute("packageSummaries", psd);
    try {
      writer.write(st.toString());
      writer.close();
    } catch (final IOException e) {
      e.printStackTrace();
    }

  }

  private void createPackageIndexPage(final PackageSummaryData psData) {
    final StringTemplateGroup group = new StringTemplateGroup("mutation_test");
    final StringTemplate st = group
        .getInstanceOf("templates/mutation/package_index");

    final Writer writer = this.outputStrategy.createWriterForFile(psData
        .getPackageDirectory() + File.separator + "index.html");
    st.setAttribute("packageData", psData);
    try {
      writer.write(st.toString());
      writer.close();
    } catch (final IOException e) {
      e.printStackTrace();
    }

  }

  public void runStart() {
    // TODO Auto-generated method stub

  }

  public void runEnd() {
    createIndexPages();
  }

  public void handleMutationResult(final MutationMetaData metaData) {
    final PackageSummaryData packageData = collectPackageSummaries(metaData);

    generateAnnotatedSourceFile(packageData.getForSourceFile(metaData
        .getFirstFileName()));

  }

}