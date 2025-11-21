package kim.biryeong.ttt.mixin;

import de.tomalbrc.danse.Danse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

@Mixin(Danse.class)
public class DanseMixin {
    @Redirect(method = "loadAnimations", at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;toAbsolutePath()Ljava/nio/file/Path;"))
    private Path modifyDanseModelPath(Path instance) {
        return instance;
    }
}
