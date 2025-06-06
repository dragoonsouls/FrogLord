package net.highwayfrogs.editor.games.konami.rescue;

import javafx.scene.Node;
import net.highwayfrogs.editor.games.generic.GameConfig;
import net.highwayfrogs.editor.games.generic.GameInstance;
import net.highwayfrogs.editor.games.generic.IGameType;
import net.highwayfrogs.editor.gui.GameConfigController;
import net.highwayfrogs.editor.gui.GameConfigController.GameConfigUIController;
import net.highwayfrogs.editor.gui.components.FolderBrowseComponent.GameConfigFolderBrowseComponent;
import net.highwayfrogs.editor.gui.components.ProgressBarComponent;
import net.highwayfrogs.editor.system.Config;
import net.highwayfrogs.editor.utils.StringUtils;
import net.highwayfrogs.editor.utils.Utils;

import java.io.File;

/**
 * Represents the game "Frogger's Adventures: The Rescue".
 * Created by Kneesnap on 8/8/2024.
 */
public class FroggerRescueGameType implements IGameType {
    public static final FroggerRescueGameType INSTANCE = new FroggerRescueGameType();
    private static final String CONFIG_MAIN_FOLDER_PATH = "mainFolderPath";

    @Override
    public String getDisplayName() {
        return "Frogger's Adventures: The Rescue";
    }

    @Override
    public String getIdentifier() {
        return "rescue";
    }

    @Override
    public GameInstance createGameInstance() {
        return new FroggerRescueInstance();
    }

    @Override
    public void loadGameInstance(GameInstance instance, String gameVersionConfigName, Config gameSetupConfig, Config instanceConfig, ProgressBarComponent progressBar) {
        if (!(instance instanceof FroggerRescueInstance))
            throw new ClassCastException("The provided instance was " + Utils.getSimpleName(instance) + ", when " + FroggerRescueInstance.class.getSimpleName() + " was required.");

        String mainFilePath = gameSetupConfig.getKeyValueNodeOrError(CONFIG_MAIN_FOLDER_PATH).getAsString();
        if (StringUtils.isNullOrWhiteSpace(mainFilePath))
            throw new IllegalArgumentException("Invalid mainFolderPath.");

        File mainFile = new File(mainFilePath);
        ((FroggerRescueInstance) instance).loadGame(gameVersionConfigName, instanceConfig, mainFile, progressBar);
    }

    @Override
    public GameConfig createConfig(String internalName) {
        return new GameConfig(internalName);
    }

    @Override
    public FroggerRescueGameConfigUI setupConfigUI(GameConfigController controller) {
        return new FroggerRescueGameConfigUI(controller);
    }

    /**
     * The UI definition for the game.
     */
    public static class FroggerRescueGameConfigUI extends GameConfigUIController<GameConfig> {
        private final GameConfigFolderBrowseComponent binFileBrowseComponent;

        public FroggerRescueGameConfigUI(GameConfigController controller) {
            super(controller, GameConfig.class);
            this.binFileBrowseComponent = new GameConfigFolderBrowseComponent(this, CONFIG_MAIN_FOLDER_PATH, "Game Data Folder", "Please locate the folder containing game data", null);
        }

        @Override
        protected void onChangeGameConfig(GameConfig oldGameConfig, Config oldEditorConfig, GameConfig newGameConfig, Config newEditorConfig) {
            this.binFileBrowseComponent.resetFolderPath();
        }

        @Override
        public boolean isLoadButtonDisabled() {
            return StringUtils.isNullOrWhiteSpace(this.binFileBrowseComponent.getCurrentFolderPath());
        }

        @Override
        protected void onControllerLoad(Node rootNode) {
            super.onControllerLoad(rootNode);
            addController(this.binFileBrowseComponent);
        }
    }
}