package pl.olafcio.forgeneo.loader;

import cpw.mods.modlauncher.api.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class NeoLoader implements ITransformationService {
    @Override
    @NotNull
    public String name() {
        return "NeoLoader";
    }

    @Override
    public void initialize(IEnvironment environment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        // TODO: Bring compat to transformation services
        //       I suppose I will rewrite them as a wrapper to this API
    }

    @Override
    @NotNull
    public List<ITransformer> transformers() {
        return List.of();
    }
}
