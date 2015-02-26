package org.sugarj.editor;

import org.sugarj.cleardep.CompilationUnit.State;
import org.sugarj.cleardep.build.BuildManager;
import org.sugarj.cleardep.build.Builder;
import org.sugarj.cleardep.build.BuilderFactory;
import org.sugarj.cleardep.stamp.ContentHashStamper;
import org.sugarj.cleardep.stamp.Stamper;
import org.sugarj.common.FileCommands;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.driver.DriverBuildRequirement;
import org.sugarj.driver.DriverInput;
import org.sugarj.driver.Environment;
import org.sugarj.driver.Result;

public class SugarLangParserBuilder extends Builder<DriverInput, EditorResult> {

  public static final Path tmpTargetDir = FileCommands.tryNewTempDir();
  
  public final static BuilderFactory<DriverInput, EditorResult, SugarLangParserBuilder> factory = new BuilderFactory<DriverInput, EditorResult, SugarLangParserBuilder>() {
    private static final long serialVersionUID = -3323446217071484245L;

    @Override
    public SugarLangParserBuilder makeBuilder(DriverInput input, BuildManager manager) {
      return new SugarLangParserBuilder(input, manager);
    }
    
  };
  
  public SugarLangParserBuilder(DriverInput input, BuildManager manager) {
    super(input, factory, manager);
  }

  @Override
  protected String taskDescription() {
    return null;
  }

  @Override
  protected Path persistentPath() {
    String depPath = FileCommands.dropExtension(input.sourceFilePath.getRelativePath()) + ".edep";
    return new RelativePath(tmpTargetDir, depPath);
  }

  @Override
  protected Class<EditorResult> resultClass() {
    return EditorResult.class;
  }

  @Override
  protected Stamper defaultStamper() {
    return ContentHashStamper.instance;
  }

  @Override
  protected void build(EditorResult result) throws Throwable {
    Result res = null;
    
    if (input.editedSourceStamp == null) {
      res = require(new DriverBuildRequirement(input));
      if (res.getDesugaringsFile() != null) {
        result.copyEditorFrom(res);
        return;
      }
    }
    
    Environment env = input.getOriginalEnvironment().clone();
    Path oldBin = env.getBin();
    env.setBin(tmpTargetDir);
    env.addToIncludePath(oldBin);
    DriverInput tmpinput = new DriverInput(env, input.baseLang, input.sourceFilePath, input.editedSource, input.editedSourceStamp, input.renamings, input.monitor, input.injectedRequirements);
    res = require(new DriverBuildRequirement(tmpinput));
    result.copyEditorFrom(res);
    
    result.setState(State.finished(result.getDesugaringsFile() != null));
  }
}
