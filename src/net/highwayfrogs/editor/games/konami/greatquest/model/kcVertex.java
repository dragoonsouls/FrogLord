package net.highwayfrogs.editor.games.konami.greatquest.model;

import lombok.Getter;
import net.highwayfrogs.editor.utils.data.reader.DataReader;
import net.highwayfrogs.editor.utils.data.writer.DataWriter;
import net.highwayfrogs.editor.games.konami.greatquest.IInfoWriter;
import net.highwayfrogs.editor.utils.NumberUtils;

import java.util.Arrays;

/**
 * Represents a single vertex. Data is optional, and is assumed to be present based on the vertex components stored separately from the vertex.
 * Created by Kneesnap on 6/22/2023.
 */
@Getter
public class kcVertex implements IInfoWriter {
    private float x;
    private float y;
    private float z;
    private float w;
    private float normalX;
    private float normalY;
    private float normalZ;
    private int diffuse;
    private float u0;
    private float v0;
    private float u1;
    private float v1;
    private float[] weight;
    private float pointSize; // Presumably only used when a vertex might be rendered as part of a POINT display list

    /**
     * Loads vertex data from the reader for the given components.
     * @param reader     The reader to load vertex data from.
     * @param components The components describing the vertex data to load.
     * @param scaleVertex if vertex scaling should be applied to compressed positions.
     */
    public void load(DataReader reader, kcVertexFormatComponent[] components, long fvf, boolean scaleVertex) {
        if (components == null)
            return;

        for (int i = 0; i < components.length; i++)
            this.load(reader, components[i], fvf, scaleVertex);
    }

    /**
     * Loads vertex data from the reader for the given components.
     * @param reader    The reader to load vertex data from.
     * @param component The component describing the vertex data to load.
     * @param scaleVertex  if vertex scaling should be applied to compressed positions.
     */
    public void load(DataReader reader, kcVertexFormatComponent component, long fvf, boolean scaleVertex) {
        if ((fvf & kcModel.FVF_FLAG_COMPRESSED) == kcModel.FVF_FLAG_COMPRESSED) {
            loadCompressed(reader, component, scaleVertex);
        } else {
            loadNormal(reader, component);
        }
    }

    /**
     * Loads vertex data from the reader for the given components.
     * @param reader    The reader to load vertex data from.
     * @param component The component describing the vertex data to load.
     */
    public void loadNormal(DataReader reader, kcVertexFormatComponent component) {
        // NOTE: If we have to implement compression, use this same function, but change readFloat() to a function which takes arguments and "decompresses" it, if that's enabled or just reads the float directly.

        float red;
        float green;
        float blue;

        switch (component) {
            case POSITION_XYZF: // 12
                this.x = reader.readFloat();
                this.y = reader.readFloat();
                this.z = reader.readFloat();
                this.w = 1F;
                break;
            case POSITION_XYZWF: // 16
                this.x = reader.readFloat();
                this.y = reader.readFloat();
                this.z = reader.readFloat();
                this.w = reader.readFloat();
                break;
            case NORMAL_XYZF: // 12
                this.normalX = reader.readFloat();
                this.normalY = reader.readFloat();
                this.normalZ = reader.readFloat();
                break;
            case NORMAL_XYZWF: // 16
                this.normalX = reader.readFloat();
                this.normalY = reader.readFloat();
                this.normalZ = reader.readFloat();
                reader.readFloat();
                // Yes, despite this one sounding like it should include W, it doesn't actually appear to assign W.
                // It only skips over it. I don't think I'm wrong because I read both the ghidra decompiled output and the raw assembly.
                // There is no 'normalW' value, so perhaps this makes sense.
                break;
            case DIFFUSE_RGBF: // 12
                red = reader.readFloat();
                green = reader.readFloat();
                blue = reader.readFloat();
                this.diffuse = (((int) (red * 255F)) << 16) | (((int) (green * 255F)) << 8) | (int) (blue * 255F);
                break;
            case DIFFUSE_RGBAF: // 16
                // Note: The PC version may keep the ALPHA in the lowest bits of the diffuse color at runtime, though Ghidra may be showing bad decompiler output, and I don't care enough to dive deep enough to answer it.
                // It shouldn't matter for us since it's runtime only, and the actual stored byte order should be correct.
                red = reader.readFloat();
                green = reader.readFloat();
                blue = reader.readFloat();
                float alpha = reader.readFloat();
                this.diffuse = (((int) (alpha * 255F)) << 24) | (((int) (red * 255F)) << 16) | (((int) (green * 255F)) << 8) | (int) (blue * 255F);
                break;
            case DIFFUSE_RGBAI: // 4
                this.diffuse = reader.readInt();
                break;
            case DIFFUSE_RGBA255F: // 16
                red = reader.readFloat();
                green = reader.readFloat();
                blue = reader.readFloat();
                alpha = reader.readFloat();
                this.diffuse = ((((int) alpha) & 0xFF) << 24) | ((((int) red) & 0xFF) << 16) | ((((int) green) & 0xFF) << 8) | (((int) blue) & 0xFF);
                break;
            case SPECULAR_RGBF: // 12
            case SPECULAR_RGBAF: // 16
            case SPECULAR_RGBAI: // 4
            case SPECULAR_RGBA255F: // 16
                // The actual code in the PS2 PAL version skips this.
                // It prints an error message, but continues reading, assuming the stride is calculable, so it can skip.
                // It is unknown if the PC version uses this yet.
                reader.skipBytes(component.getStride());
                throw new RuntimeException("Cannot read unsupported vertex format " + component + ".");
            case WEIGHT1F: // 4
                if (this.weight == null || this.weight.length != 1)
                    this.weight = new float[1];
                this.weight[0] = reader.readFloat();
                break;
            case WEIGHT2F: // 8
                if (this.weight == null || this.weight.length != 2)
                    this.weight = new float[2];
                this.weight[0] = reader.readFloat();
                this.weight[1] = reader.readFloat();
                break;
            case TEX1F: // 8
                this.u0 = reader.readFloat();
                this.v0 = reader.readFloat();
                break;
            case TEX2F: // 16
                this.u0 = reader.readFloat();
                this.v0 = reader.readFloat();
                this.u1 = reader.readFloat();
                this.v1 = reader.readFloat();
                break;
            case TEX1_STQP: // 16
                this.u0 = reader.readFloat();
                this.v0 = reader.readFloat();
                reader.skipBytes(8); // Not sure why we skip it, but that's what the PS2 PAL version does.
                break;
            case WEIGHT3F: // 12
                if (this.weight == null || this.weight.length != 3)
                    this.weight = new float[3];

                this.weight[0] = reader.readFloat();
                this.weight[1] = reader.readFloat();
                this.weight[2] = reader.readFloat();
                break;
            case WEIGHT4F: // 16
                if (this.weight == null || this.weight.length != 4)
                    this.weight = new float[4];

                this.weight[0] = reader.readFloat();
                this.weight[1] = reader.readFloat();
                this.weight[2] = reader.readFloat();
                this.weight[3] = reader.readFloat();
                break;
            case MATRIX_INDICES: // 16
                // Unused / unimplemented. This behavior matches PS2 PAL.
                reader.skipBytes(16);
                break;
            case PSIZE: // 4
                this.pointSize = reader.readFloat();
                break;
            default:
                throw new RuntimeException("Cannot read vertex data due to unsupported kcVertexFormatComponent " + component);
        }
    }

    // kcModelRender() -> Calls kcGraphicsSetRenderState(KCRS_COMPVERTSCALE, 0.006666667), then afterward it gets set back to 1.0.
    // This sets the scale of compressed vertex floats. The real game somehow configures the PS2 to apply the compressed vertex scaling itself.
    // However, we apply the scaling on load/save to just keep it simple.
    private static final int COMPRESSED_POSITION_UP_SCALE = 150;
    private static final double COMPRESSED_POSITION_DOWN_SCALE = 1D / COMPRESSED_POSITION_UP_SCALE; // The game uses .006666667 instead.
    private static final int COMPRESSION_FIXED_PT_MAIN_UNIT = 4096;
    private static final int COMPRESSION_FIXED_PT_OTHER_UNIT = 16;
    private static final int COMPRESSION_FIXED_PT_POSITION_UNIT = COMPRESSION_FIXED_PT_OTHER_UNIT * COMPRESSED_POSITION_UP_SCALE;
    private static final float COMPRESSION_MAIN_MULTIPLIER = 1F / COMPRESSION_FIXED_PT_MAIN_UNIT;
    private static final float COMPRESSION_OTHER_MULTIPLIER = 1F / COMPRESSION_FIXED_PT_OTHER_UNIT;
    private static final double COMPRESSION_POSITION_MULTIPLIER = COMPRESSED_POSITION_DOWN_SCALE / COMPRESSION_FIXED_PT_OTHER_UNIT;

    private static float readCompressedFloat(DataReader reader) {
        return readCompressedFloat(reader, COMPRESSION_MAIN_MULTIPLIER);
    }

    private static float readCompressedFloat(DataReader reader, double multiplier) {
        return (float) (reader.readShort() * multiplier);
    }

    /**
     * Loads vertex data from the reader for the given components.
     * This method has been verified against both the PS2 PAL and PC versions.
     * @param reader    The reader to load vertex data from.
     * @param component The component describing the vertex data to load.
     * @param scaleVertex if vertex scaling should be applied to compressed positions.
     */
    public void loadCompressed(DataReader reader, kcVertexFormatComponent component, boolean scaleVertex) {
        short red;
        short green;
        short blue;
        short alpha;
        final double positionMultiplier = scaleVertex ? COMPRESSION_POSITION_MULTIPLIER : COMPRESSION_OTHER_MULTIPLIER;

        switch (component) {
            case POSITION_XYZF: // 6
                this.x = readCompressedFloat(reader, positionMultiplier);
                this.y = readCompressedFloat(reader, positionMultiplier);
                this.z = readCompressedFloat(reader, positionMultiplier);
                this.w = 1F;
                break;
            case POSITION_XYZWF: // 8
                this.x = readCompressedFloat(reader, positionMultiplier);
                this.y = readCompressedFloat(reader, positionMultiplier);
                this.z = readCompressedFloat(reader, positionMultiplier);
                this.w = readCompressedFloat(reader, positionMultiplier);
                break;
            case NORMAL_XYZF: // 6
                this.normalX = readCompressedFloat(reader);
                this.normalY = readCompressedFloat(reader);
                this.normalZ = readCompressedFloat(reader);
                break;
            case NORMAL_XYZWF: // 8
                this.normalX = readCompressedFloat(reader);
                this.normalY = readCompressedFloat(reader);
                this.normalZ = readCompressedFloat(reader);
                readCompressedFloat(reader); // Unused, there is no "normalW" value.
                break;
            case DIFFUSE_RGBAF: // 8
                // Honestly... I don't think this works on the PS2 version. Perhaps it's just bad ghidra decompiler output, but the PS2 PAL version doesn't seem to handle values right.
                // The PC version is also confusing.
                // We follow the next code since it seems that might be what should happen here.
            case DIFFUSE_RGBA255F: // 8
                red = (short) (reader.readShort() & 0xFF);
                green = (short) (reader.readShort() & 0xFF);
                blue = (short) (reader.readShort() & 0xFF);
                alpha = (short) (reader.readShort() & 0xFF);
                this.diffuse = (((int) alpha) << 24) | (((int) red) << 16) | (((int) green) << 8) | ((int) blue);
                break;
            case DIFFUSE_RGBF: // 6
            case DIFFUSE_RGBAI: // 4
            case SPECULAR_RGBF: // 6
            case SPECULAR_RGBAF: // 8
            case SPECULAR_RGBAI: // 4
            case SPECULAR_RGBA255F: // 8
                // The actual code in the PS2 PAL version skips this.
                // It prints an error message, but continues reading, assuming the stride is calculable, so it can skip.
                // It is unknown if the PC version uses this yet.
                reader.skipBytes(component.getCompressedStride());
                throw new RuntimeException("Cannot read unsupported vertex format " + component + ".");
            case WEIGHT1F: // 2
                if (this.weight == null || this.weight.length != 1)
                    this.weight = new float[1];
                this.weight[0] = readCompressedFloat(reader);
                break;
            case WEIGHT2F: // 4
                if (this.weight == null || this.weight.length != 2)
                    this.weight = new float[2];
                this.weight[0] = readCompressedFloat(reader);
                this.weight[1] = readCompressedFloat(reader);
                break;
            case TEX1F: // 4
                this.u0 = readCompressedFloat(reader);
                this.v0 = readCompressedFloat(reader);
                break;
            case TEX2F: // 8
                this.u0 = readCompressedFloat(reader);
                this.v0 = readCompressedFloat(reader);
                this.u1 = readCompressedFloat(reader);
                this.v1 = readCompressedFloat(reader);
                break;
            case TEX1_STQP: // 8
                this.u0 = readCompressedFloat(reader);
                this.v0 = readCompressedFloat(reader);
                reader.skipBytes(4); // Not sure why we skip it, but that's what the PS2 PAL & PC versions do.
                break;
            case WEIGHT3F: // 6
                if (this.weight == null || this.weight.length != 3)
                    this.weight = new float[3];

                this.weight[0] = readCompressedFloat(reader);
                this.weight[1] = readCompressedFloat(reader);
                this.weight[2] = readCompressedFloat(reader);
                break;
            case WEIGHT4F: // 8
                if (this.weight == null || this.weight.length != 4)
                    this.weight = new float[4];

                this.weight[0] = readCompressedFloat(reader);
                this.weight[1] = readCompressedFloat(reader);
                this.weight[2] = readCompressedFloat(reader);
                this.weight[3] = readCompressedFloat(reader);
                break;
            case MATRIX_INDICES: // 8
                // Unused / unimplemented. This behavior matches PS2 PAL & PC.
                reader.skipBytes(8);
                break;
            case PSIZE: // 2
                this.pointSize = readCompressedFloat(reader, COMPRESSION_OTHER_MULTIPLIER);
                break;
            default:
                throw new RuntimeException("Cannot read compressed vertex data due to unsupported kcVertexFormatComponent " + component);
        }
    }

    private static void writeCompressedFloat(DataWriter writer, float value) {
        writeCompressedFloat(writer, value, COMPRESSION_FIXED_PT_MAIN_UNIT);
    }

    private static void writeCompressedFloat(DataWriter writer, float value, int unit) {
        int temp = (int) Math.round((double) value * unit);
        if (temp > Short.MAX_VALUE || temp < Short.MIN_VALUE)
            throw new RuntimeException("Cannot save the value '" + value + "' while compression is enabled for the model, because this coordinate is too extreme to represent. in a 16 bit number. (" + temp + ")");

        writer.writeShort((short) temp);
    }

    /**
     * Saves vertex data to the writer for the given components.
     * @param writer     The writer to write vertex data from.
     * @param components The components describing the vertex data to write.
     * @param scaleVertex if vertex scaling should be applied to compressed positions.
     */
    public void save(DataWriter writer, kcVertexFormatComponent[] components, long fvf, boolean scaleVertex) {
        if (components == null)
            return;

        for (int i = 0; i < components.length; i++)
            this.save(writer, components[i], fvf, scaleVertex);
    }

    /**
     * Saves vertex data to the writer for the given component.
     * @param writer    The writer to write vertex data from.
     * @param component The component describing the vertex data to write.
     * @param scaleVertex if vertex scaling should be applied to compressed positions.
     */
    public void save(DataWriter writer, kcVertexFormatComponent component, long fvf, boolean scaleVertex) {
        if ((fvf & kcModel.FVF_FLAG_COMPRESSED) == kcModel.FVF_FLAG_COMPRESSED) {
            saveCompressed(writer, component, scaleVertex);
        } else {
            saveNormal(writer, component);
        }
    }

    /**
     * Saves vertex data to the writer for the given component.
     * @param writer    The writer to write vertex data from.
     * @param component The component describing the vertex data to write.
     */
    public void saveNormal(DataWriter writer, kcVertexFormatComponent component) {
        switch (component) {
            case POSITION_XYZF: // 12
                writer.writeFloat(this.x);
                writer.writeFloat(this.y);
                writer.writeFloat(this.z);
                break;
            case POSITION_XYZWF: // 16
                writer.writeFloat(this.x);
                writer.writeFloat(this.y);
                writer.writeFloat(this.z);
                writer.writeFloat(this.w);
                break;
            case NORMAL_XYZF: // 12
                writer.writeFloat(this.normalX);
                writer.writeFloat(this.normalY);
                writer.writeFloat(this.normalZ);
                break;
            case NORMAL_XYZWF: // 16
                writer.writeFloat(this.normalX);
                writer.writeFloat(this.normalY);
                writer.writeFloat(this.normalZ);
                writer.writeFloat(1F); // Unused value for 'W'.
                break;
            case DIFFUSE_RGBF: // 12
                writer.writeFloat(getDiffuseRed());
                writer.writeFloat(getDiffuseGreen());
                writer.writeFloat(getDiffuseBlue());
                break;
            case DIFFUSE_RGBAF: // 16
                writer.writeFloat(getDiffuseRed());
                writer.writeFloat(getDiffuseGreen());
                writer.writeFloat(getDiffuseBlue());
                writer.writeFloat(getDiffuseAlpha());
                break;
            case DIFFUSE_RGBAI: // 4
                writer.writeInt(this.diffuse);
                break;
            case DIFFUSE_RGBA255F: // 16
                writer.writeFloat(getDiffuseRed255F());
                writer.writeFloat(getDiffuseGreen255F());
                writer.writeFloat(getDiffuseBlue255F());
                writer.writeFloat(getDiffuseAlpha255F());
                break;
            case SPECULAR_RGBF: // 12
            case SPECULAR_RGBAF: // 16
            case SPECULAR_RGBAI: // 4
            case SPECULAR_RGBA255F: // 16
                // The actual code in the PS2 PAL version skips this.
                // It prints an error message, but continues reading, assuming the stride is calculable, so it can skip.
                // It is unknown if the PC version uses this yet.
                writer.writeNull(component.getStride());
                throw new RuntimeException("Cannot write unsupported vertex format " + component + ".");
            case WEIGHT1F: // 4
                if (this.weight == null || this.weight.length != 1)
                    this.weight = new float[1];
                writer.writeFloat(this.weight[0]);
                break;
            case WEIGHT2F: // 8
                if (this.weight == null || this.weight.length != 2)
                    this.weight = new float[2];
                writer.writeFloat(this.weight[0]);
                writer.writeFloat(this.weight[1]);
                break;
            case TEX1F: // 8
                writer.writeFloat(this.u0);
                writer.writeFloat(this.v0);
                break;
            case TEX2F: // 16
                writer.writeFloat(this.u0);
                writer.writeFloat(this.v0);
                writer.writeFloat(this.u1);
                writer.writeFloat(this.v1);
                break;
            case TEX1_STQP: // 16
                writer.writeFloat(this.u0);
                writer.writeFloat(this.v0);
                writer.writeFloat(1F); // Unused (Value seen in C001.VTX)
                writer.writeFloat(0F); // Unused (Value seen in C001.VTX)
                break;
            case WEIGHT3F: // 12
                if (this.weight == null || this.weight.length != 3)
                    this.weight = new float[3];

                writer.writeFloat(this.weight[0]);
                writer.writeFloat(this.weight[1]);
                writer.writeFloat(this.weight[2]);
                break;
            case WEIGHT4F: // 16
                if (this.weight == null || this.weight.length != 4)
                    this.weight = new float[4];

                writer.writeFloat(this.weight[0]);
                writer.writeFloat(this.weight[1]);
                writer.writeFloat(this.weight[2]);
                writer.writeFloat(this.weight[3]);
                break;
            case MATRIX_INDICES: // 16
                // Unused / unimplemented. This behavior matches PS2 PAL.
                writer.writeNull(16);
                break;
            case PSIZE: // 4
                writer.writeFloat(this.pointSize);
                break;
            default:
                throw new RuntimeException("Cannot read vertex data due to unsupported kcVertexFormatComponent " + component);
        }
    }

    /**
     * Saves vertex data to the writer for the given component.
     * @param writer    The writer to write vertex data from.
     * @param component The component describing the vertex data to write.
     * @param scaleVertex if vertex scaling should be applied to compressed positions.
     */
    public void saveCompressed(DataWriter writer, kcVertexFormatComponent component, boolean scaleVertex) {
        final int positionUnit = scaleVertex ? COMPRESSION_FIXED_PT_POSITION_UNIT : COMPRESSION_FIXED_PT_OTHER_UNIT;
        switch (component) {
            case POSITION_XYZF: // 6
                writeCompressedFloat(writer, this.x, positionUnit);
                writeCompressedFloat(writer, this.y, positionUnit);
                writeCompressedFloat(writer, this.z, positionUnit);
                break;
            case POSITION_XYZWF: // 8
                writeCompressedFloat(writer, this.x, positionUnit);
                writeCompressedFloat(writer, this.y, positionUnit);
                writeCompressedFloat(writer, this.z, positionUnit);
                writeCompressedFloat(writer, this.w, positionUnit);
                break;
            case NORMAL_XYZF: // 6
                writeCompressedFloat(writer, this.normalX);
                writeCompressedFloat(writer, this.normalY);
                writeCompressedFloat(writer, this.normalZ);
                break;
            case NORMAL_XYZWF: // 8
                writeCompressedFloat(writer, this.normalX);
                writeCompressedFloat(writer, this.normalY);
                writeCompressedFloat(writer, this.normalZ);
                writeCompressedFloat(writer, 1F); // Unused value for 'W'.
                break;
            case DIFFUSE_RGBAF: // 8
                // May not be valid.
                writer.writeFloat((short) getDiffuseRed());
                writer.writeFloat((short) getDiffuseGreen());
                writer.writeFloat((short) getDiffuseBlue());
                writer.writeFloat((short) getDiffuseAlpha());
                break;
            case DIFFUSE_RGBA255F: // 8
                writer.writeShort((short) getDiffuseRed255F());
                writer.writeShort((short) getDiffuseGreen255F());
                writer.writeShort((short) getDiffuseBlue255F());
                writer.writeShort((short) getDiffuseAlpha255F());
                break;
            case DIFFUSE_RGBF: // 6
            case DIFFUSE_RGBAI: // 4
            case SPECULAR_RGBF: // 6
            case SPECULAR_RGBAF: // 8
            case SPECULAR_RGBAI: // 4
            case SPECULAR_RGBA255F: // 8
                // The actual code in the PS2 PAL version skips this.
                // It prints an error message, but continues reading, assuming the stride is calculable, so it can skip.
                // It is unknown if the PC version uses this yet.
                writer.writeNull(component.getCompressedStride());
                throw new RuntimeException("Cannot write unsupported vertex format " + component + ".");
            case WEIGHT1F: // 2
                if (this.weight == null || this.weight.length != 1)
                    this.weight = new float[1];
                writeCompressedFloat(writer, this.weight[0]);
                break;
            case WEIGHT2F: // 4
                if (this.weight == null || this.weight.length != 2)
                    this.weight = new float[2];
                writeCompressedFloat(writer, this.weight[0]);
                writeCompressedFloat(writer, this.weight[1]);
                break;
            case TEX1F: // 4
                writeCompressedFloat(writer, this.u0);
                writeCompressedFloat(writer, this.v0);
                break;
            case TEX2F: // 8
                writeCompressedFloat(writer, this.u0);
                writeCompressedFloat(writer, this.v0);
                writeCompressedFloat(writer, this.u1);
                writeCompressedFloat(writer, this.v1);
                break;
            case TEX1_STQP: // 8
                writeCompressedFloat(writer, this.u0);
                writeCompressedFloat(writer, this.v0);
                writeCompressedFloat(writer, 1F); // Unused
                writeCompressedFloat(writer, 1F); // Unused
                break;
            case WEIGHT3F: // 6
                if (this.weight == null || this.weight.length != 3)
                    this.weight = new float[3];

                writeCompressedFloat(writer, this.weight[0]);
                writeCompressedFloat(writer, this.weight[1]);
                writeCompressedFloat(writer, this.weight[2]);
                break;
            case WEIGHT4F: // 8
                if (this.weight == null || this.weight.length != 4)
                    this.weight = new float[4];

                writeCompressedFloat(writer, this.weight[0]);
                writeCompressedFloat(writer, this.weight[1]);
                writeCompressedFloat(writer, this.weight[2]);
                writeCompressedFloat(writer, this.weight[3]);
                break;
            case MATRIX_INDICES: // 8
                // Unused / unimplemented. This behavior matches PS2 PAL.
                writer.writeNull(8);
                break;
            case PSIZE: // 2
                writeCompressedFloat(writer, this.pointSize, COMPRESSION_FIXED_PT_OTHER_UNIT);
                break;
            default:
                throw new RuntimeException("Cannot read vertex data due to unsupported kcVertexFormatComponent " + component);
        }
    }

    /**
     * Gets the red component of the diffuse color as a floating point number from 0 to 1F.
     * @return colorComponent
     */
    public float getDiffuseRed() {
        return ((this.diffuse >> 16) & 0xFF) / 255F;
    }

    /**
     * Gets the red component of the diffuse color as a floating point number from 0 to 255F.
     * @return colorComponent
     */
    public float getDiffuseRed255F() {
        return ((this.diffuse >> 16) & 0xFF);
    }

    /**
     * Gets the green component of the diffuse color as a floating point number from 0 to 1F.
     * @return colorComponent
     */
    public float getDiffuseGreen() {
        return ((this.diffuse >> 8) & 0xFF) / 255F;
    }

    /**
     * Gets the green component of the diffuse color as a floating point number from 0 to 255F.
     * @return colorComponent
     */
    public float getDiffuseGreen255F() {
        return ((this.diffuse >> 8) & 0xFF);
    }

    /**
     * Gets the blue component of the diffuse color as a floating point number from 0 to 1F.
     * @return colorComponent
     */
    public float getDiffuseBlue() {
        return (this.diffuse & 0xFF) / 255F;
    }

    /**
     * Gets the blue component of the diffuse color as a floating point number from 0 to 255F.
     * @return colorComponent
     */
    public float getDiffuseBlue255F() {
        return (this.diffuse & 0xFF);
    }

    /**
     * Gets the alpha component of the diffuse color as a floating point number from 0 to 1F.
     * @return colorComponent
     */
    public float getDiffuseAlpha() {
        return ((this.diffuse >> 24) & 0xFF) / 255F;
    }

    /**
     * Gets the alpha component of the diffuse color as a floating point number from 0 to 255F.
     * @return colorComponent
     */
    public float getDiffuseAlpha255F() {
        return ((this.diffuse >> 24) & 0xFF);
    }

    @Override
    public void writeInfo(StringBuilder builder) {
        builder.append("kcVertex{pos=[").append(this.x).append(",").append(this.y).append(",").append(this.z);
        builder.append("},normal={").append(this.normalX).append(",").append(this.normalY).append(",").append(this.normalZ);
        builder.append("},diffuse=").append(NumberUtils.to0PrefixedHexString(this.diffuse));
        builder.append(",uv0=[").append(this.u0).append(",").append(this.v0);
        builder.append("],uv1=[").append(this.u1).append(",").append(this.v1);
        builder.append("],weight=").append(Arrays.toString(this.weight)).append(",w=").append(this.w).append(",pointSize=").append(this.pointSize);
    }
}