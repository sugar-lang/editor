package org.sugarj.editor;

import org.sugarj.driver.Result;

public class EditorResult extends Result {
  private static final long serialVersionUID = -9158207069569931390L;

  public void copyEditorFrom(Result other) {
    editorServices = other.getEditorServices();
    collectedErrors = other.getCollectedErrors();
    parseErrors = other.getParseErrors();
    sugaredSyntaxTree = other.getSugaredSyntaxTree();
    desugaredSyntaxTree = other.getDesugaredSyntaxTree();
    parseTableFile = other.getParseTable();
    desugaringsFile = other.getDesugaringsFile();
  }
  
  protected boolean isConsistentExtend() {
    if (desugaringsFile == null)
      return false;
    return super.isConsistentExtend();
  }

}
