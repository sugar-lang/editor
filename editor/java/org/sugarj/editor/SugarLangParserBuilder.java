package org.sugarj.editor;

import org.sugarj.cleardep.BuildUnit.State;
import org.sugarj.cleardep.build.Builder;
import org.sugarj.cleardep.build.BuilderFactory;
import org.sugarj.cleardep.stamp.FileHashStamper;
import org.sugarj.cleardep.stamp.Stamper;
import org.sugarj.common.FileCommands;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.driver.DriverBuildRequest;
import org.sugarj.driver.DriverInput;
import org.sugarj.driver.Environment;
import org.sugarj.driver.Result;

public class SugarLangParserBuilder extends Builder<DriverInput, EditorResult> {

  public static final Path tmpTargetDir = FileCommands.tryNewTempDir();
  
  public final static BuilderFactory<DriverInput, EditorResult, SugarLangParserBuilder> factory = new BuilderFactory<DriverInput, EditorResult, SugarLangParserBuilder>() {
    private static final long serialVersionUID = -3323446217071484245L;

    @Override
    public SugarLangParserBuilder makeBuilder(DriverInput input) {
      return new SugarLangParserBuilder(input);
    }
    
  };
  
  public SugarLangParserBuilder(DriverInput input) {
    super(input);
  }

  @Override
  protected String description() {
    return null;
  }

  @Override
  protected Path persistentPath() {
    String depPath = FileCommands.dropExtension(input.sourceFilePath.getRelativePath()) + ".edep";
    return new RelativePath(tmpTargetDir, depPath);
  }

  @Override
  protected Stamper defaultStamper() {
    return FileHashStamper.instance;
  }

  @Override
  protected EditorResult build() throws Throwable {
    EditorResult result = new EditorResult();
    Result res = null;
    
    if (input.editedSourceStamp == null) {
      res = requireBuild(new DriverBuildRequest(input));
      if (res.getDesugaringsFile() != null) {
        result.copyEditorFrom(res);
        return result;
      }
    }
    
    Environment env = input.getOriginalEnvironment().clone();
    Path oldBin = env.getBin();
    env.setBin(tmpTargetDir);
    env.addToIncludePath(oldBin);
    DriverInput tmpinput = new DriverInput(env, input.baseLang, input.sourceFilePath, input.editedSource, input.editedSourceStamp, input.renamings, input.monitor, input.injectedRequirements);
    res = requireBuild(new DriverBuildRequest(tmpinput));
    result.copyEditorFrom(res);
    
    setState(State.finished(result.getDesugaringsFile() != null));
    
    return result;
  }
}
