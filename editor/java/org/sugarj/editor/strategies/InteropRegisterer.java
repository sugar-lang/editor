package org.sugarj.editor.strategies;

import org.strategoxt.lang.JavaInteropRegisterer;
import org.sugarj.transformations.analysis.AnalysisDataInterop;

public class InteropRegisterer extends JavaInteropRegisterer {

  public InteropRegisterer() {
    super(AnalysisDataInterop.instance.getStrategies());
  }
}
