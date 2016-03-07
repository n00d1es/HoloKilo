/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kajos.holokilo.Orientation;

public class Vector3
{
    public float x;
    public float y;
    public float z;

    public Vector3()
    {
        set(0,0,0);
    }

    public Vector3(float xx, float yy, float zz)
    {
        set(xx, yy, zz);
    }


    public void setComponent(int i, float val)
    {
        if (i == 0)
            x = val;
        else if (i == 1)
            y = val;
        else
            z = val;
    }

    public void setZero()
    {
        x = (this.y = this.z = 0.0f);
    }

    public void set(Vector3 other)
    {
        x = other.x;
        y = other.y;
        z = other.z;
    }

    public void set(float xx, float yy, float zz)
    {
        x = xx;
        y = yy;
        z = zz;
    }

    public void add(Vector3 other)
    {
        x += other.x;
        y += other.y;
        z += other.z;
    }

    public void add(float xx, float yy, float zz)
    {
        x += xx;
        y += yy;
        z += zz;
    }

    public void scale(float s)
    {
        x *= s;
        y *= s;
        z *= s;
    }

    public void normalize()
    {
        float d = length();
        if (d != 0.0f)
            scale(1.0f / d);
    }

    public static float dot(Vector3 a, Vector3 b)
    {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    public float length()
    {
        return (float)Math.sqrt(x * x + y * y + z * z);
    }

    public boolean sameValues(Vector3 other)
    {
        return (x == other.x) && (y == other.y) && (z == other.z);
    }

    public static void sub(Vector3 a, Vector3 b, Vector3 result)
    {
        result.set(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    public static void cross(Vector3 a, Vector3 b, Vector3 result)
    {
        result.set(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    public static void ortho(Vector3 v, Vector3 result)
    {
        int k = largestAbsComponent(v) - 1;
        if (k < 0) {
            k = 2;
        }
        result.setZero();
        result.setComponent(k, 1.0f);

        cross(v, result, result);
        result.normalize();
    }

    public static int largestAbsComponent(Vector3 v)
    {
        float xAbs = Math.abs(v.x);
        float yAbs = Math.abs(v.y);
        float zAbs = Math.abs(v.z);

        if (xAbs > yAbs) {
            if (xAbs > zAbs) {
                return 0;
            }
            return 2;
        }

        if (yAbs > zAbs) {
            return 1;
        }
        return 2;
    }
}