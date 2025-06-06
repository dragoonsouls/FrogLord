package net.highwayfrogs.editor.utils.data.writer;

import lombok.Getter;
import net.highwayfrogs.editor.Constants;
import net.highwayfrogs.editor.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An in-memory data receiver.
 * Created by Kneesnap on 8/13/2018.
 */
@Getter
public class ArrayReceiver implements DataReceiver {
    private final List<Byte> bytes;
    private int index;

    public ArrayReceiver() {
        this.bytes = new ArrayList<>();
    }

    public ArrayReceiver(int startingSize) {
        this.bytes = new ArrayList<>(startingSize);
    }

    @Override
    public void writeByte(byte value)  {
        while (this.index > bytes.size()) // Add data up to the index.
            bytes.add(Constants.NULL_BYTE);

        if (bytes.size() > this.index) { // Writing over existing bytes.
            bytes.set(this.index, value);
        } else { // Index matches exactly. Append at end.
            bytes.add(value);
        }

        this.index++; // Increment index.
    }

    @Override
    public void writeBytes(byte[] values) {
        for (byte value : values)
            writeByte(value);
    }

    @Override
    public void writeBytes(byte[] values, int offset, int amount) throws IOException {
        amount = Math.max(0, Math.min(amount, values.length - offset - 1));
        if (amount == 0)
            return;

        for (int i = 0; i < amount; i++)
            writeByte(values[offset + i]);
    }

    @Override
    public void setIndex(int newIndex) {
        this.index = newIndex;
    }

    /**
     * Get the array of bytes.
     * @return array
     */
    public byte[] toArray() {
        return Utils.toArray(bytes);
    }
}