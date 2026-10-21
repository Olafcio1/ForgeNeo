package pl.olafcio.forgeneo.mod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ForgeNeo.MODID)
public class ForgeNeo {

    public static final String MODID = "forgeneo";
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeNeo.class);

    public ForgeNeo() {
//        ClassTransformer
//        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
