package org.sugarj.editor;

import org.eclipse.imp.preferences.PreferencesService;
import org.eclipse.imp.runtime.PluginBase;
import org.osgi.framework.BundleContext;

public class Activator extends PluginBase 
{ 
  public static final String kPluginID = "Sugarlang";

  public static final String kLanguageName = "Sugarlang";

  protected static Activator sPlugin;

  public static Activator getInstance()
  { 
    if(sPlugin == null)
      return new Activator();
    return sPlugin;
  }

  public Activator () 
  { 
    super();
    sPlugin = this;
  }

  @Override public void start(BundleContext context) throws Exception
  { 
    super.start(context);
    // force loading of SugarJ IMP plugin 
    SugarLangParseController.getDescriptor();
  }

  @Override public String getID()
  { 
    return kPluginID;
  }

  @Override public String getLanguageID()
  { 
    return kLanguageName;
  }

  protected static PreferencesService preferencesService = null;
}