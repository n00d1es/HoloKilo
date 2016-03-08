package net.kajos.holokilo.Orientation;

public class Matrix3x3
{
    public float[] m = new float[9];

    public Matrix3x3()
    {
        setIdentity();
    }

    public void setToRotation (Vector3 axis, float degrees) {
        setToRotation(axis, (float)Math.cos(Math.PI * 180f / degrees), (float)Math.sin(Math.PI * 180f / degrees));
    }

    public void setToRotation (Vector3 axis, float cos, float sin) {
        float oc = 1.0f - cos;
        m[0] = oc * axis.x * axis.x + cos;
        m[3] = oc * axis.x * axis.y - axis.z * sin;
        m[6] = oc * axis.z * axis.x + axis.y * sin;
        m[1] = oc * axis.x * axis.y + axis.z * sin;
        m[4] = oc * axis.y * axis.y + cos;
        m[7] = oc * axis.y * axis.z - axis.x * sin;
        m[2] = oc * axis.z * axis.x - axis.y * sin;
        m[5] = oc * axis.y * axis.z + axis.x * sin;
        m[8] = oc * axis.z * axis.z + cos;
    }

    public Matrix3x3(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22)
    {
        m[0] = m00;
        m[1] = m01;
        m[2] = m02;
        m[3] = m10;
        m[4] = m11;
        m[5] = m12;
        m[6] = m20;
        m[7] = m21;
        m[8] = m22;
    }

    public Matrix3x3(Matrix3x3 o)
    {
        m[0] = o.m[0];
        m[1] = o.m[1];
        m[2] = o.m[2];
        m[3] = o.m[3];
        m[4] = o.m[4];
        m[5] = o.m[5];
        m[6] = o.m[6];
        m[7] = o.m[7];
        m[8] = o.m[8];
    }

    public void set(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22)
    {
        m[0] = m00;
        m[1] = m01;
        m[2] = m02;
        m[3] = m10;
        m[4] = m11;
        m[5] = m12;
        m[6] = m20;
        m[7] = m21;
        m[8] = m22;
    }

    public void set(Matrix3x3 o)
    {
        m[0] = o.m[0];
        m[1] = o.m[1];
        m[2] = o.m[2];
        m[3] = o.m[3];
        m[4] = o.m[4];
        m[5] = o.m[5];
        m[6] = o.m[6];
        m[7] = o.m[7];
        m[8] = o.m[8];
    }

    public void setZero()
    {
        float tmp63_62 = (m[2] = m[3] = m[4] = m[5] = m[6] = m[7] = m[8] = 0.0f); m[1] = tmp63_62; m[0] = tmp63_62;
    }

    public void setIdentity()
    {
        float tmp41_40 = (m[3] = m[5] = m[6] = m[7] = 0.0f); m[2] = tmp41_40; m[1] = tmp41_40;
        float tmp63_62 = (m[8] = 1.0f); m[4] = tmp63_62; m[0] = tmp63_62;
    }

    public void setSameDiagonal(float d)
    {
        float tmp19_18 = (m[8] = d); m[4] = tmp19_18; m[0] = tmp19_18;
    }

    public float yaw()
    {
        return (float) Math.atan2(m[2], m[6]) / (float)Math.PI * 180f;
    }

    public float pitch()
    {
        return (float) Math.asin(-m[4]) / (float)Math.PI * 180f;
    }

    public float roll()
    {
        return (float) Math.atan2(m[2], m[3]) / (float)Math.PI * 180f;
    }

    public float get(int row, int col)
    {
        return m[(3 * row + col)];
    }

    public void set(int row, int col, float value)
    {
        m[(3 * row + col)] = value;
    }

    public void getColumn(int col, Vector3 v)
    {
        v.x = m[col];
        v.y = m[(col + 3)];
        v.z = m[(col + 6)];
    }

    public void setColumn(int col, Vector3 v)
    {
        m[col] = v.x;
        m[(col + 3)] = v.y;
        m[(col + 6)] = v.z;
    }

    public void scale(float s)
    {
        m[0] *= s;
        m[1] *= s;
        m[2] *= s;
        m[3] *= s;
        m[4] *= s;
        m[5] *= s;
        m[6] *= s;
        m[7] *= s;
        m[8] *= s;
    }

    public void plusEquals(Matrix3x3 b)
    {
        m[0] += b.m[0];
        m[1] += b.m[1];
        m[2] += b.m[2];
        m[3] += b.m[3];
        m[4] += b.m[4];
        m[5] += b.m[5];
        m[6] += b.m[6];
        m[7] += b.m[7];
        m[8] += b.m[8];
    }

    public void minusEquals(Matrix3x3 b)
    {
        m[0] -= b.m[0];
        m[1] -= b.m[1];
        m[2] -= b.m[2];
        m[3] -= b.m[3];
        m[4] -= b.m[4];
        m[5] -= b.m[5];
        m[6] -= b.m[6];
        m[7] -= b.m[7];
        m[8] -= b.m[8];
    }

    public void transpose()
    {
        float tmp = m[1];
        m[1] = m[3];
        m[3] = tmp;

        tmp = m[2];
        m[2] = m[6];
        m[6] = tmp;

        tmp = m[5];
        m[5] = m[7];
        m[7] = tmp;
    }

    public void transpose(Matrix3x3 result)
    {
        float m1 = m[1];
        float m2 = m[2];
        float m5 = m[5];
        result.m[0] = m[0];
        result.m[1] = m[3];
        result.m[2] = m[6];
        result.m[3] = m1;
        result.m[4] = m[4];
        result.m[5] = m[7];
        result.m[6] = m2;
        result.m[7] = m5;
        result.m[8] = m[8];
    }

    public static void add(Matrix3x3 a, Matrix3x3 b, Matrix3x3 result)
    {
        result.m[0] = a.m[0] + b.m[0];
        result.m[1] = a.m[1] + b.m[1];
        result.m[2] = a.m[2] + b.m[2];
        result.m[3] = a.m[3] + b.m[3];
        result.m[4] = a.m[4] + b.m[4];
        result.m[5] = a.m[5] + b.m[5];
        result.m[6] = a.m[6] + b.m[6];
        result.m[7] = a.m[7] + b.m[7];
        result.m[8] = a.m[8] + b.m[8];
    }

    public static void mult(Matrix3x3 a, Matrix3x3 b, Matrix3x3 result)
    {
        result.set(a.m[0] * b.m[0] + a.m[1] * b.m[3] + a.m[2] * b.m[6], a.m[0] * b.m[1] + a.m[1] * b.m[4] + a.m[2] * b.m[7], a.m[0] * b.m[2] + a.m[1] * b.m[5] + a.m[2] * b.m[8], a.m[3] * b.m[0] + a.m[4] * b.m[3] + a.m[5] * b.m[6], a.m[3] * b.m[1] + a.m[4] * b.m[4] + a.m[5] * b.m[7], a.m[3] * b.m[2] + a.m[4] * b.m[5] + a.m[5] * b.m[8], a.m[6] * b.m[0] + a.m[7] * b.m[3] + a.m[8] * b.m[6], a.m[6] * b.m[1] + a.m[7] * b.m[4] + a.m[8] * b.m[7], a.m[6] * b.m[2] + a.m[7] * b.m[5] + a.m[8] * b.m[8]);
    }

    public static void mult(Matrix3x3 a, Vector3 v, Vector3 result)
    {
        float x = a.m[0] * v.x + a.m[1] * v.y + a.m[2] * v.z;
        float y = a.m[3] * v.x + a.m[4] * v.y + a.m[5] * v.z;
        float z = a.m[6] * v.x + a.m[7] * v.y + a.m[8] * v.z;
        result.x = x;
        result.y = y;
        result.z = z;
    }

    public float determinant()
    {
        return get(0, 0) * (get(1, 1) * get(2, 2) - get(2, 1) * get(1, 2)) - get(0, 1) * (get(1, 0) * get(2, 2) - get(1, 2) * get(2, 0)) + get(0, 2) * (get(1, 0) * get(2, 1) - get(1, 1) * get(2, 0));
    }

    public boolean invert(Matrix3x3 result)
    {
        float d = determinant();
        if (d == 0.0D) {
            return false;
        }

        float invdet = 1.0f / d;

        result.set((m[4] * m[8] - m[7] * m[5]) * invdet, -(m[1] * m[8] - m[2] * m[7]) * invdet, (m[1] * m[5] - m[2] * m[4]) * invdet, -(m[3] * m[8] - m[5] * m[6]) * invdet, (m[0] * m[8] - m[2] * m[6]) * invdet, -(m[0] * m[5] - m[3] * m[2]) * invdet, (m[3] * m[7] - m[6] * m[4]) * invdet, -(m[0] * m[7] - m[6] * m[1]) * invdet, (m[0] * m[4] - m[3] * m[1]) * invdet);

        return true;
    }

    private static Vector3 tmp = new Vector3();
    private static Vector3 tmp2 = new Vector3();
    private static final Vector3 fixed = new Vector3(0,0,1);
    public static double angle(Matrix3x3 a, Matrix3x3 b) {
        Matrix3x3.mult(a, fixed, tmp);
        Matrix3x3.mult(b, fixed, tmp2);
        return Vector3.dot(tmp, tmp2);
    }
}