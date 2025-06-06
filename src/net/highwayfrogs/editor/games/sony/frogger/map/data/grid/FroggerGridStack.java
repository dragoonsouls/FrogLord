package net.highwayfrogs.editor.games.sony.frogger.map.data.grid;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.highwayfrogs.editor.file.standard.IVector;
import net.highwayfrogs.editor.file.standard.SVector;
import net.highwayfrogs.editor.games.sony.SCGameObject;
import net.highwayfrogs.editor.games.sony.frogger.FroggerGameInstance;
import net.highwayfrogs.editor.games.sony.frogger.map.FroggerMapFile;
import net.highwayfrogs.editor.games.sony.frogger.map.mesh.FroggerMapPolygon;
import net.highwayfrogs.editor.utils.DataUtils;
import net.highwayfrogs.editor.utils.NumberUtils;
import net.highwayfrogs.editor.utils.Utils;
import net.highwayfrogs.editor.utils.data.reader.DataReader;
import net.highwayfrogs.editor.utils.data.writer.DataWriter;
import net.highwayfrogs.editor.utils.logging.ILogger;
import net.highwayfrogs.editor.utils.logging.InstanceLogger.LazyInstanceLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the GRID_STACK struct.
 * Created by Kneesnap on 8/27/2018.
 */
public class FroggerGridStack extends SCGameObject<FroggerGameInstance> {
    @Getter private final FroggerMapFile mapFile;
    @Getter private final int x;
    @Getter private final int z;
    @Getter private final List<FroggerGridSquare> gridSquares = new ArrayList<>();

    // The tl;dr of the following is that the cliff height is automatically generated, and has accuracy problems.
    // In some cases, this can cause cliff deaths to not apply at the correct times.
    // Specifically, cave deaths will not work properly below Y=0 (generally will not let the player step onto a cliff at all) or above Y=1020 (player will die regardless of height).

    // The following value was always zero/unused before PSX Build 30 (August 19th 1997).
    // Its only use is for cliff deaths. Specifically, in FROG.C, if the target grid square is a CLIFF_DEATH, AND the height here is >= the player's height, do a bouncy cobweb "movement blocked" animation, instead of killing the player.
    // Prior to Build 30, the function did not exist, raising doubt on the accuracy of the comment date. Instead, the height of the individual grid square was calculated.
    // An example of how this caused issues can be seen on SUB1, where the bottom left & right cliff tiles at the bird pickup spot kill Frogger when hopping from an adjacent tile in build 29. However, in build 30 they do not.

    // The reason why the height tested must be based on the stack height instead of the grid square height is because some grid squares (especially triangles) are pretty much flat.
    // And, the only real way to tell that the cliff portion is higher up is to factor in height data from other vertices in the same grid stack.

    // After some sleuthing, it seems this is based on averaging the vertices in the area of the grid stack.
    // A good example to demonstrate why is the water sections in SUB1 where there are tiles at y=0 underneath water at y=-32.
    // The water quads are large, taking up 3x3 grid tiles each, and only the grid stacks directly under the water vertices which get their cliff height boosted to y=-4.

    // Unfortunately, because only an unsigned byte has been allocated, any cliff height below Y=0 or above Y=1020 is impossible to represent properly.
    // This can even be seen in original maps such as SUB2.MAP. Upon previewing the collision grid cliff heights many of them will be seen high in the air due to wrapping around.
    // Because of this, it is strongly recommended to use 0, 0, 0 as the base of the level.
    // So, a warning has been added to the grid square reaction editor to warn in most situations when the error might occur.

    // Finally, the algorithm used to generate cliff heights by FrogLord is unable to perfectly replicate the original data.
    // This isn't because the algorithm is different, but instead because FrogLord does not have the original float32-based vertex positions that Mappy used.
    // The current theory is that when mappy exported the vertex float32 values to fixed16 values, they were rounded to the nearest float16.
    // This means that the rounded values seen in the game .MAP files have a different cliff height than the more-precise values found in the original Mappy .iv files which the .MAP files were created from.
    // As such, the cliff heights generated by FrogLord may not always perfectly match the original, but are extremely close and work as well as the original.
    @Getter @Setter private byte rawCliffHeightValue;
    private transient short loadSquareCount = -1;

    public static final int MAX_GRID_DIMENSION = 256;

    // The grid X/Z threshold (unit is 1/16th of the grid square length) for considering a nearby vertex as part of this grid square.
    // This was determined by testing all thresholds to find what yielded the closest cliff heights to the original game.
    // More information can be found above, and while this isn't perfect, it's the best we can get due to not having the original 32-bit float coordinates.
    public static final int NEARBY_VERTEX_THRESHOLD = 25;
    public static final float CLIFF_Y_THRESHOLD_MIN = 0F;
    public static final float CLIFF_Y_THRESHOLD_MAX = -1024F;

    public FroggerGridStack(FroggerMapFile mapFile, int x, int z) {
        super(mapFile != null ? mapFile.getGameInstance() : null);
        this.mapFile = mapFile;
        this.x = x;
        this.z = z;
    }

    /**
     * Loads the stack data.
     * @param reader the reader to read data from.
     * @param expectedSquareIndex the index we expect to start reading square data from.
     * @return the position we expect the next grid stack to read squares from
     */
    public int load(DataReader reader, int expectedSquareIndex) {
        this.loadSquareCount = reader.readUnsignedByteAsShort();
        this.rawCliffHeightValue = reader.readByte();
        int realSquareIndex = reader.readUnsignedShortAsInt();
        if (realSquareIndex != expectedSquareIndex)
            throw new RuntimeException("The expected grid square index (" + expectedSquareIndex + ") did not match the grid square index in the game data (" + realSquareIndex + ").");

        return expectedSquareIndex + this.loadSquareCount;
    }

    /**
     * Reads the grid squares from the current position.
     * @param reader the reader to read it from
     */
    public void loadGridSquares(DataReader reader) {
        if (this.loadSquareCount < 0)
            throw new RuntimeException("Cannot read grid squares from " + NumberUtils.toHexString(reader.getIndex()) + ", the amount of squares to load was set to " + this.loadSquareCount + ".");

        this.gridSquares.clear();
        for (int i = 0; i < this.loadSquareCount; i++) {
            FroggerGridSquare newGridSquare = new FroggerGridSquare(this);
            newGridSquare.load(reader);
            this.gridSquares.add(newGridSquare);
        }

        this.loadSquareCount = -1;
    }

    /**
     * Saves the grid stack data to the writer.
     * @param writer the writer to write the grid stack data to
     * @param gridSquareStartIndex the index into the grid squares array which our grid squares will be written to
     * @return the position the next grid stack will write its grid squares to
     */
    public int save(DataWriter writer, int gridSquareStartIndex) {
        writer.writeUnsignedByte((short) this.gridSquares.size());
        writer.writeByte(this.rawCliffHeightValue);
        writer.writeUnsignedShort(gridSquareStartIndex);
        return gridSquareStartIndex + this.gridSquares.size();
    }

    /**
     * Writes the grid squares from the current position.
     * @param writer the writer to write it to
     */
    public void saveGridSquares(DataWriter writer) {
        for (int i = 0; i < this.gridSquares.size(); i++)
            this.gridSquares.get(i).save(writer);
    }

    /**
     * Gets the logger information.
     */
    public String getLoggerInfo() {
        return this.mapFile != null ? this.mapFile.getFileDisplayName() + "|GridStack{x=" + this.x + ",z=" + this.z + "}" : Utils.getSimpleName(this);
    }

    @Override
    public ILogger getLogger() {
        return new LazyInstanceLogger(getGameInstance(), FroggerGridStack::getLoggerInfo, this);
    }

    /**
     * Clear all information about the square.
     */
    public void clear() {
        this.gridSquares.clear();
        this.rawCliffHeightValue = 0;
    }

    /**
     * Gets the cliff height as a floating point number.
     * This is used to prevent the player from walking into a cliff higher than their current position.
     * An example of this behavior is seen in Lily Islands (SUB1) at the bird pickup spot to the purple frog.
     * @return height
     */
    public float getCliffHeightAsFloat() {
        return DataUtils.fixedPointIntToFloat4Bit((this.rawCliffHeightValue & 0xFF) << 6);
    }

    /**
     * Set the average world height of this stack.
     * This is used to prevent the player from walking into a cliff higher than their current position.
     * An example of this behavior is seen in Lily Islands (SUB1) at the bird pickup spot to the purple frog.
     * @param newHeight The new height.
     */
    public void setCliffHeight(float newHeight) {
        // Yes, this is extremely lossy. See the rawCliffHeightValue documentation for details.
        this.rawCliffHeightValue = (byte) (DataUtils.floatToFixedPointInt4Bit(newHeight) >> 6);
    }

    /**
     * Calculates the cliff height of the stack.
     * This has been determined to be the average of all "nearby" vertices.
     * See the documentation of {@code rawCliffHeightValue} for a full explanation.
     * @return cliffHeightInt
     */
    public static int calculateCliffHeight(List<SVector> nearbyVertices) {
        if (nearbyVertices == null || nearbyVertices.isEmpty())
            return 0;

        int vertexSumY = 0;
        for (int i = 0; i < nearbyVertices.size(); i++)
            vertexSumY += nearbyVertices.get(i).getY();

        // No usable grid squares.
        return -vertexSumY / nearbyVertices.size();
    }

    /**
     * Calculates the raw cliff height value for the stack.
     * This has been determined to be the average of all "nearby" vertices.
     * See the documentation of {@code rawCliffHeightValue} for a full explanation.
     * @return rawCliffHeightValue
     */
    public static byte calculateRawCliffHeightValue(List<SVector> nearbyVertices) {
        // Negative numbers wrap-around to max height.
        return (byte) (calculateCliffHeight(nearbyVertices) >> 6);
    }

    /**
     * Calculates the world Y of the highest square in the stack.
     * @return highestGridSquareY
     */
    public float getHighestGridSquareYAsFloat() {
        for (int i = this.gridSquares.size() - 1; i >= 0; i--) {
            FroggerGridSquare square = this.gridSquares.get(i);
            if (square.getPolygon() != null)
                return DataUtils.fixedPointIntToFloat4Bit(square.calculateAverageWorldHeight());
        }

        // No usable grid squares.
        return -getCliffHeightAsFloat();
    }

    /**
     * Reimplementation of GRID.C/GetGridInfoFromWorldXZ
     */
    public FroggerGridStackInfo getGridStackInfo() {
        if (this.gridSquares.isEmpty())
            return null;

        FroggerGridSquare gridSquare = this.gridSquares.get(0);
        FroggerMapPolygon polygon = gridSquare.getPolygon();
        if (polygon == null)
            return null;

        // GRID_SQUARE points to a map poly, which we take to define two semi-infinite tris.
        // Work out which one we are over, then project onto it.
        int dx = this.x % MAX_GRID_DIMENSION;
        int dz = this.z % MAX_GRID_DIMENSION;

        int gridY;
        IVector xSlope = new IVector(MAX_GRID_DIMENSION, 0, 0);
        IVector zSlope = new IVector(0, 0, MAX_GRID_DIMENSION);
        List<SVector> vertices = getMapFile().getVertexPacket().getVertices();
        if (dz >= dx) {
            // Top left tri
            int mapPolyY0 = vertices.get(polygon.getVertices()[0]).getY();
            int mapPolyY1 = vertices.get(polygon.getVertices()[1]).getY();
            int mapPolyY2 = vertices.get(polygon.getVertices()[2]).getY();

            dz = MAX_GRID_DIMENSION - dz;
            gridY = mapPolyY0 + ((dx * (mapPolyY1 - mapPolyY0)) / MAX_GRID_DIMENSION) + ((dz * (mapPolyY2 - mapPolyY0)) / MAX_GRID_DIMENSION);
            xSlope.setY(mapPolyY1 - mapPolyY0);
            zSlope.setY(mapPolyY0 - mapPolyY2);
        } else {
            // Bottom right tri
            int mapPolyY1 = vertices.get(polygon.getVertices()[1]).getY();
            int mapPolyY2 = vertices.get(polygon.getVertices()[2]).getY();
            int mapPolyY3 = polygon.getPolygonType().isQuad() ? vertices.get(polygon.getVertices()[3]).getY() : mapPolyY2; // mapPolyY2 has been validated by the warning which plays if the padding vertex does not match.

            dx = MAX_GRID_DIMENSION - dx;
            gridY = mapPolyY3 + ((dx * (mapPolyY2 - mapPolyY3)) / MAX_GRID_DIMENSION) + ((dz * (mapPolyY1 - mapPolyY3)) / MAX_GRID_DIMENSION);
            xSlope.setY(mapPolyY3 - mapPolyY2);
            zSlope.setY(mapPolyY1 - mapPolyY3);
        }

        xSlope.normalise();
        zSlope.normalise();
        return new FroggerGridStackInfo(gridY, xSlope, zSlope);
    }

    @Getter
    @RequiredArgsConstructor
    public static class FroggerGridStackInfo {
        private final int y;
        private final IVector xSlope;
        private final IVector zSlope;
    }
}