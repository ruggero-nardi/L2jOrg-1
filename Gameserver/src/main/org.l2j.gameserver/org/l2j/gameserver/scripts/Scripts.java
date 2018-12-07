package  org.l2j.gameserver.scripts;

import org.apache.commons.lang3.ClassUtils;
import org.l2j.commons.compiler.Compiler;
import org.l2j.commons.compiler.MemoryClassLoader;
import org.l2j.commons.listener.Listener;
import org.l2j.commons.listener.ListenerList;
import org.l2j.gameserver.Config;
import org.l2j.gameserver.handler.bypass.Bypass;
import org.l2j.gameserver.handler.bypass.BypassHolder;
import org.l2j.gameserver.listener.script.OnInitScriptListener;
import org.l2j.gameserver.listener.script.OnLoadScriptListener;
import org.l2j.gameserver.model.Player;
import org.l2j.gameserver.model.instances.NpcInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static java.util.Objects.isNull;

public class Scripts
{
    public class ScriptListenerImpl extends ListenerList<Scripts>
    {
        public void load()
        {
            for(Listener<Scripts> listener : getListeners())
                if(OnLoadScriptListener.class.isInstance(listener))
                    ((OnLoadScriptListener) listener).onLoad();
        }

        public void init()
        {
            for(Listener<Scripts> listener : getListeners())
                if(OnInitScriptListener.class.isInstance(listener))
                    ((OnInitScriptListener) listener).onInit();
        }
    }

    private static final Logger _log = LoggerFactory.getLogger(Scripts.class);

    private static final Scripts _instance = new Scripts();

    public static Scripts getInstance()
    {
        return _instance;
    }

    private final Map<String, Class<?>> _classes = new TreeMap<String, Class<?>>();
    private final ScriptListenerImpl _listeners = new ScriptListenerImpl();

    private Scripts()
    {
        load();
    }

    /**
     * Вызывается при загрузке сервера. Загрузает все скрипты в data/scripts. Не инициирует объекты и обработчики.
     *
     * @return true, если загрузка прошла успешно
     */
    private void load()
    {
        File f = new File("./lib/scripts.jar");
        if(f.exists())
        {
            _log.info("Scripts: Loading library...");

            try(JarInputStream stream = new JarInputStream(new FileInputStream(f))) {
                JarEntry entry = null;
                while((entry = stream.getNextJarEntry()) != null) {
                    //Вложенные класс
                    if(entry.getName().contains(ClassUtils.INNER_CLASS_SEPARATOR) || !entry.getName().endsWith(".class"))
                        continue;

                    String name = entry.getName().replace(".class", "").replace("/", ".");

                    Class<?> clazz = getClass().getClassLoader().loadClass(name);
                    if(Modifier.isAbstract(clazz.getModifiers()))
                        continue;

                    _classes.put(clazz.getName(), clazz);
                }
            } catch(Exception e) {
                throw new Error("Failed loading scripts library!");
            }

        }

        _log.info("Scripts: Loading...");

        List<Class<?>> classes = load(new File(Config.DATAPACK_ROOT, "data/scripts"));

        if(classes.isEmpty())
            throw new Error("Failed loading scripts!");

        for(Class<?> clazz : classes)
            _classes.put(clazz.getName(), clazz);

        _log.info("Scripts: Loaded " + _classes.size() + " classes.");

        _listeners.load();
    }

    /**
     * Вызывается при загрузке сервера. Инициализирует объекты и обработчики.
     */
    public void init()
    {
        for(Class<?> clazz : _classes.values())
            init(clazz);

        _listeners.init();
    }

    /**
     * Загрузить все классы в data/scripts/target
     *
     * @param target путь до класса, или каталога со скриптами
     * @return список загруженых скриптов
     */
    public List<Class<?>> load(File target) {
        File[] scriptFiles = null;
        if(target.isFile()) {
            scriptFiles = new File[] { target} ;
        }
        else if(target.isDirectory())
        {
            _log.debug("Loading Scripts from {} ", target.getAbsolutePath());
            try(var paths = Files.walk(target.toPath())) {
                scriptFiles = paths.filter(this::acceptJavaFile).map(Path::toFile).toArray(File[]::new);
            } catch (IOException e) {
                _log.error(e.getLocalizedMessage(), e);
            }

        }

        if(isNull(scriptFiles)) {
            return Collections.emptyList();
        }

        List<Class<?>> classes = new ArrayList<>();
        Compiler compiler = new Compiler();

        if(compiler.compile(scriptFiles))
        {
            MemoryClassLoader classLoader = compiler.getClassLoader();
            for(String name : classLoader.getLoadedClasses())
            {
                //Вложенные класс
                if(name.contains(ClassUtils.INNER_CLASS_SEPARATOR))
                    continue;

                try
                {
                    Class<?> clazz = classLoader.loadClass(name);
                    if(Modifier.isAbstract(clazz.getModifiers()))
                        continue;
                    classes.add(clazz);

                    try
                    {
                        if(OnLoadScriptListener.class.isAssignableFrom(clazz))
                        {
                            if(OnInitScriptListener.class.isAssignableFrom(clazz))
                                _log.warn("Scripts: Error in class: " + clazz.getName() + ". Can not use OnLoad and OnInit listeners together!");

                            for(Method method : clazz.getMethods())
                            {
                                if(method.isAnnotationPresent(Bypass.class))
                                {
                                    _log.warn("Scripts: Error in class: " + clazz.getName() + ". Can not use OnLoad listener and bypass annotation together!");
                                    break;
                                }
                            }

                            _listeners.add((OnLoadScriptListener) clazz.getDeclaredConstructor().newInstance());
                        }
                    }
                    catch(Exception e)
                    {
                        _log.error("", e);
                    }
                }
                catch(ClassNotFoundException e)
                {
                    _log.error("Scripts: Can't load script class: " + name, e);
                    classes.clear();
                    break;
                }
            }
        }

        return classes;
    }

    private boolean acceptJavaFile(Path p) {
        var stringPath = p.toString();
        return stringPath.endsWith(".java") && !stringPath.endsWith("module-info.java");
    }

    private Object init(Class<?> clazz)
    {
        if(OnLoadScriptListener.class.isAssignableFrom(clazz))
            return null;

        Object o = null;
        try
        {
            if(OnInitScriptListener.class.isAssignableFrom(clazz))
            {
                o = clazz.getDeclaredConstructor().newInstance();
                _listeners.add((OnInitScriptListener)o);
            }

            for(Method method : clazz.getMethods())
                if(method.isAnnotationPresent(Bypass.class))
                {
                    Bypass an = method.getAnnotation(Bypass.class);
                    if(o == null)
                        o = clazz.getDeclaredConstructor().newInstance();
                    Class<?>[] par = method.getParameterTypes();
                    if(par.length == 0 || par[0] != Player.class || par[1] != NpcInstance.class || par[2] != String[].class)
                    {
                        _log.error("Wrong parameters for bypass method: " + method.getName() + ", class: " + clazz.getSimpleName());
                        continue;
                    }

                    BypassHolder.getInstance().registerBypass(an.value(), o, method);
                }
        }
        catch(Exception e)
        {
            _log.error("", e);
        }
        return o;
    }


    public Map<String, Class<?>> getClasses()
    {
        return _classes;
    }
}