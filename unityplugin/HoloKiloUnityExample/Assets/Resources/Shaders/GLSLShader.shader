Shader "GLSLShader" {
	Properties {
		_MainTex("Base (RGB)", 2D) = "white" {}
	}

	SubShader {
		Tags{ "RenderType" = "Opaque" }
		ZWrite Off
		Lighting Off
		ZTest Always
		Fog{ Mode Off }

		Pass{
			GLSLPROGRAM

			#ifdef VERTEX

			varying vec2 TextureCoordinate;

			void main()
			{
				gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
				TextureCoordinate = gl_MultiTexCoord0.xy;
			}

			#endif

			#ifdef FRAGMENT
			#extension GL_OES_EGL_image_external : require

			uniform samplerExternalOES _MainTex;
			uniform float _Exposure;
			varying vec2 TextureCoordinate;

			void main()
			{
				gl_FragColor = texture2D(_MainTex, TextureCoordinate) * _Exposure;
			}

			#endif

			ENDGLSL
		}
	}
}
