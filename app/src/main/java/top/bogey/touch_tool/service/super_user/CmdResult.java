package top.bogey.touch_tool.service.super_user;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class CmdResult implements Parcelable {
    private final boolean result;
    private final String output;
    // 命令退出码：-1 表示未知（异常 / 老版本序列化兼容）
    private final int exitCode;

    public CmdResult(boolean result, String output) {
        this(result, output, -1);
    }

    public CmdResult(boolean result, String output, int exitCode) {
        this.result = result;
        this.output = output;
        this.exitCode = exitCode;
    }

    protected CmdResult(Parcel in) {
        result = in.readByte() != 0;
        output = in.readString();
        exitCode = in.readInt();
    }

    public static final Creator<CmdResult> CREATOR = new Creator<>() {
        @Override
        public CmdResult createFromParcel(Parcel in) {
            return new CmdResult(in);
        }

        @Override
        public CmdResult[] newArray(int size) {
            return new CmdResult[size];
        }
    };

    public boolean getResult() {
        return result;
    }

    public String getOutput() {
        return output;
    }

    public int getExitCode() {
        return exitCode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByte((byte) (result ? 1 : 0));
        dest.writeString(output);
        dest.writeInt(exitCode);
    }
}
