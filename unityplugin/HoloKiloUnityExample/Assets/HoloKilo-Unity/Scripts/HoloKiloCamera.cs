using UnityEngine;
using System.Collections;

public class HoloKiloCamera : MonoBehaviour
{
	public bool updateCameraMatrix = true;
    public Light ambientLight;
    public Camera secondCamera;
    public GameObject[] trackedObjects;
    public int scaleRenderTexture = 2;

    private RenderTexture renderTexture;
    private Camera firstCamera;

    private int width;
    private int height;

    private AndroidJavaClass androidClass;

    private Matrix4x4 viewMatrixLeft = new Matrix4x4();
    private Matrix4x4 viewMatrixRight = new Matrix4x4();

    private bool drawCardboard = true;

	private Matrix4x4 rotateMatrix = new Matrix4x4();
	
    void Start ()
    {
        Quaternion rotation = Quaternion.Euler(0, 0, 90);
		rotateMatrix.SetTRS(new Vector3(0,0,0), rotation, new Vector3(1,1,1));
		
        androidClass = new AndroidJavaClass("net.kajos.holokilo.Unity.UnityRenderer");

        firstCamera = GetComponent<Camera>();

        if (secondCamera != null)
        {
            secondCamera.rect = new Rect(0.5f, 0, 0.5f, 1f);
        }

        Screen.sleepTimeout = SleepTimeout.NeverSleep;
    }

	AndroidJavaObject androidObject = null;
    void OnPreRender()
    {
        if (androidObject != null)
        {
            if (androidObject.Call<bool>("isStopped"))
                return;

            width = androidObject.Call<int>("getScreenWidth") / scaleRenderTexture;
            height = androidObject.Call<int>("getScreenHeight") / scaleRenderTexture;

            if (renderTexture != null)
            {
                // Has screen size changed, then update rendertexture
                if (renderTexture.width != width || renderTexture.height != height)
                {
                    // Discard previous rendertexture, make new one
                    renderTexture.DiscardContents();
                    Debug.Log("Update with width: " + width + " height: " + height);
                    renderTexture = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32);

                    // Set cameras textures
                    firstCamera.targetTexture = renderTexture;
                    if (secondCamera != null)
                        secondCamera.targetTexture = renderTexture;

                    float cameraVFov = androidObject.Call<float>("getSceneVFov");
                    float cameraAspect = androidObject.Call<float>("getSceneAspect");
                    firstCamera.fieldOfView = cameraVFov;
                    firstCamera.aspect = cameraAspect;
                    if (secondCamera != null)
                    {
                        secondCamera.fieldOfView = cameraVFov;
                        secondCamera.aspect = cameraAspect;
                    }

                    androidObject.Call("setUnityTextureID", renderTexture.GetNativeTexturePtr().ToInt32());

                    firstCamera.Render();

                    drawCardboard = androidObject.Call<bool>("hasCardboard");
                    if (drawCardboard)
                    {
                        firstCamera.rect = new Rect(0, 0, 0.5f, 1f);

                        // Be sure to call render here or will never update
                        if (secondCamera != null) {
							secondCamera.enabled = true;
                            secondCamera.Render();
						}
                    }
                    else
                    {
                        if (secondCamera != null) {
							secondCamera.enabled = false;
						}
                        firstCamera.rect = new Rect(0, 0, 1f, 1f);
                    }
                }
            }
			else
			{
				Debug.Log("Create with width: " + width + " height: " + height);
				renderTexture = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32);

				firstCamera.targetTexture = renderTexture;
				if (secondCamera != null)
					secondCamera.targetTexture = renderTexture;
				// Be sure to call render here or will never update
				firstCamera.Render();
				if (secondCamera != null)
					secondCamera.Render();

				float cameraVFov = androidObject.Call<float>("getSceneVFov");
				float cameraAspect = androidObject.Call<float>("getSceneAspect");
				firstCamera.fieldOfView = cameraVFov;
				firstCamera.aspect = cameraAspect;
				if (secondCamera != null)
				{
					secondCamera.fieldOfView = cameraVFov;
					secondCamera.aspect = cameraAspect;
				}

				androidObject.Call("setUnityTextureID", renderTexture.GetNativeTexturePtr().ToInt32());

				drawCardboard = androidObject.Call<bool>("hasCardboard");
				if (drawCardboard)
				{
					if (secondCamera != null) {
						secondCamera.enabled = true;
					}
					firstCamera.rect = new Rect(0, 0, 0.5f, 1f);
				}
				else
				{
					if (secondCamera != null) {
						secondCamera.enabled = false;
					}
					firstCamera.rect = new Rect(0, 0, 1f, 1f);
				}
			}

            if (ambientLight != null)
            {
                float[] color = androidObject.Call<float[]>("getAmbientColor");
                ambientLight.color = new Color(color[0] / 255f, color[1] / 255f, color[2] / 255f, 1f);
            }

            float[] translations = androidObject.Call<float[]>("getTranslations");
            for (int i = 0, k = 0; i < translations.Length && k < trackedObjects.Length; i += 3, k++)
            {
                if (trackedObjects[k] != null)
                {
                    trackedObjects[k].transform.position = new Vector3(translations[i], translations[i + 1], translations[i + 2]);
                    Debug.Log("Position " + k + ": " + translations[i] + ", " + translations[i+1] + ", " + translations[i+2]);
                }
            }
			
			CameraUpdate();
        } else {
			androidObject = androidClass.CallStatic<AndroidJavaObject>("getInstance");
		}
    }

    void CameraUpdate()
    {
		if (updateCameraMatrix) {
			if (drawCardboard)
			{
				float[] viewBoth = androidObject.Call<float[]>("getViewMatrixBoth");

				viewMatrixLeft.m00 = viewBoth[0];
				viewMatrixLeft.m01 = viewBoth[4];
				viewMatrixLeft.m02 = viewBoth[8];
				viewMatrixLeft.m03 = viewBoth[12];
				viewMatrixLeft.m10 = viewBoth[1];
				viewMatrixLeft.m11 = viewBoth[5];
				viewMatrixLeft.m12 = viewBoth[9];
				viewMatrixLeft.m13 = viewBoth[13];
				viewMatrixLeft.m20 = viewBoth[2];
				viewMatrixLeft.m21 = viewBoth[6];
				viewMatrixLeft.m22 = viewBoth[10];
				viewMatrixLeft.m23 = viewBoth[14];
				viewMatrixLeft.m30 = viewBoth[3];
				viewMatrixLeft.m31 = viewBoth[7];
				viewMatrixLeft.m32 = viewBoth[11];
				viewMatrixLeft.m33 = viewBoth[15];

				viewMatrixRight.m00 = viewBoth[16];
				viewMatrixRight.m01 = viewBoth[20];
				viewMatrixRight.m02 = viewBoth[24];
				viewMatrixRight.m03 = viewBoth[28];
				viewMatrixRight.m10 = viewBoth[17];
				viewMatrixRight.m11 = viewBoth[21];
				viewMatrixRight.m12 = viewBoth[25];
				viewMatrixRight.m13 = viewBoth[29];
				viewMatrixRight.m20 = viewBoth[18];
				viewMatrixRight.m21 = viewBoth[22];
				viewMatrixRight.m22 = viewBoth[26];
				viewMatrixRight.m23 = viewBoth[30];
				viewMatrixRight.m30 = viewBoth[19];
				viewMatrixRight.m31 = viewBoth[23];
				viewMatrixRight.m32 = viewBoth[27];
				viewMatrixRight.m33 = viewBoth[31];
				
				firstCamera.worldToCameraMatrix = viewMatrixLeft;
				if (secondCamera != null)
					secondCamera.worldToCameraMatrix = viewMatrixRight;
			} else
			{
				float[] viewLeft = androidObject.Call<float[]>("getViewMatrixLeft");
				
				viewMatrixLeft.m00 = viewLeft[0];
				viewMatrixLeft.m01 = viewLeft[4];
				viewMatrixLeft.m02 = viewLeft[8];
				viewMatrixLeft.m03 = viewLeft[12];
				viewMatrixLeft.m10 = viewLeft[1];
				viewMatrixLeft.m11 = viewLeft[5];
				viewMatrixLeft.m12 = viewLeft[9];
				viewMatrixLeft.m13 = viewLeft[13];
				viewMatrixLeft.m20 = viewLeft[2];
				viewMatrixLeft.m21 = viewLeft[6];
				viewMatrixLeft.m22 = viewLeft[10];
				viewMatrixLeft.m23 = viewLeft[14];
				viewMatrixLeft.m30 = viewLeft[3];
				viewMatrixLeft.m31 = viewLeft[7];
				viewMatrixLeft.m32 = viewLeft[11];
				viewMatrixLeft.m33 = viewLeft[15];

				firstCamera.worldToCameraMatrix = viewMatrixLeft;
			}
		}
    }

    void SetCameraTexture(string value)
    {
        Debug.Log("Load texture: " + value);
    }
}
