package im.laura.scripts.lua.libraries;

import im.laura.scripts.interpreter.LuaValue;
import im.laura.scripts.interpreter.compiler.jse.CoerceJavaToLua;
import im.laura.scripts.interpreter.lib.OneArgFunction;
import im.laura.scripts.interpreter.lib.TwoArgFunction;
import im.laura.scripts.lua.classes.ModuleClass;

public class ModuleLibrary extends TwoArgFunction {

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaValue library = tableOf();
        library.set("register", new register());

        env.set("module", library);
        return library;
    }

    public class register extends OneArgFunction {

        @Override
        public LuaValue call(LuaValue arg) {
            return CoerceJavaToLua.coerce(new ModuleClass(arg.toString()));
        }

    }

}
