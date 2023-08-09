/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.lounge

import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.login.anonymous.AnonymousUser
import app.lounge.login.anonymous.AnonymousUserRetrofitAPI
import app.lounge.login.anonymous.AnonymousUserRetrofitImpl
import app.lounge.networking.NetworkResult
import com.aurora.gplayapi.data.models.AuthData
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Properties
import java.util.concurrent.TimeUnit

class AnonymousUserAPITest {

    companion object {
        var authData: AuthData? =  null
    }

    @Test
    fun testOnSuccessReturnsAuthData() = runBlocking {
        val response = anonymousUser.requestAuthData(
            anonymousAuthDataRequestBody = requestBodyData,
        )
        when(response){
            is NetworkResult.Success -> authData = response.data
            is NetworkResult.Error -> { }
        }

        assert(authData is AuthData) { "Assert!! Success must return data" }
    }


    private fun retrofitTestConfig(
        baseUrl: String,
        timeoutInMillisecond: Long = 10000L
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .callTimeout(timeoutInMillisecond, TimeUnit.MILLISECONDS)
                .build()
        )
        .build()

    private val eCloudTest = retrofitTestConfig(AnonymousUserRetrofitAPI.tokenBaseURL)

    private val anonymousUser: AnonymousUser = AnonymousUserRetrofitImpl(eCloud = eCloudTest)

    private val requestBodyData = AnonymousAuthDataRequestBody(
        properties = testSystemProperties,
        userAgent = testUserAgent
    )
}

const val testUserAgent: String = "{\"package\":\"foundation.e.apps.debug\",\"version\":\"2.5.5.debug\",\"device\":\"coral\",\"api\":32,\"os_version\":\"1.11-s-20230511288805-dev-coral\",\"build_id\":\"319e25cd.20230630224839\"}"

val testSystemProperties = Properties().apply {
    setProperty("UserReadableName", "coral-default")
    setProperty("Build.HARDWARE", "coral")
    setProperty("Build.RADIO", "g8150-00123-220402-B-8399852")
    setProperty("Build.FINGERPRINT","google/coral/coral:12/SQ3A.220705.003.A1/8672226:user/release-keys")
    setProperty("Build.BRAND", "google")
    setProperty("Build.DEVICE", "coral")
    setProperty("Build.VERSION.SDK_INT", "32")
    setProperty("Build.VERSION.RELEASE", "12")
    setProperty("Build.MODEL", "Pixel 4 XL")
    setProperty("Build.MANUFACTURER", "Google")
    setProperty("Build.PRODUCT", "coral")
    setProperty("Build.ID", "SQ3A.220705.004")
    setProperty("Build.BOOTLOADER", "c2f2-0.4-8351033")
    setProperty("TouchScreen", "3")
    setProperty("Keyboard", "1")
    setProperty("Navigation", "1")
    setProperty("ScreenLayout", "2")
    setProperty("HasHardKeyboard", "false")
    setProperty("HasFiveWayNavigation", "false")
    setProperty("Screen.Density", "560")
    setProperty("Screen.Width", "1440")
    setProperty("Screen.Height", "2984")
    setProperty("Platforms", "arm64-v8a,armeabi-v7a,armeabi")
    setProperty("Features", "android.hardware.sensor.proximity,com.verizon.hardware.telephony.lte,com.verizon.hardware.telephony.ehrpd,android.hardware.sensor.accelerometer,android.software.controls,android.hardware.faketouch,com.google.android.feature.D2D_CABLE_MIGRATION_FEATURE,android.hardware.telephony.euicc,android.hardware.reboot_escrow,android.hardware.usb.accessory,android.hardware.telephony.cdma,android.software.backup,android.hardware.touchscreen,android.hardware.touchscreen.multitouch,android.software.print,org.lineageos.weather,android.software.activities_on_secondary_displays,android.hardware.wifi.rtt,com.google.android.feature.PIXEL_2017_EXPERIENCE,android.software.voice_recognizers,android.software.picture_in_picture,android.hardware.sensor.gyroscope,android.hardware.audio.low_latency,android.software.vulkan.deqp.level,android.software.cant_save_state,com.google.android.feature.PIXEL_2018_EXPERIENCE,android.hardware.security.model.compatible,com.google.android.feature.PIXEL_2019_EXPERIENCE,android.hardware.opengles.aep,org.lineageos.livedisplay,org.lineageos.profiles,android.hardware.bluetooth,android.hardware.camera.autofocus,android.hardware.telephony.gsm,android.hardware.telephony.ims,android.software.incremental_delivery,android.software.sip.voip,android.hardware.se.omapi.ese,android.software.opengles.deqp.level,android.hardware.usb.host,android.hardware.audio.output,android.software.verified_boot,android.hardware.camera.flash,android.hardware.camera.front,android.hardware.sensor.hifi_sensors,com.google.android.apps.photos.PIXEL_2019_PRELOAD,android.hardware.se.omapi.uicc,android.hardware.strongbox_keystore,android.hardware.screen.portrait,android.hardware.nfc,com.google.android.feature.TURBO_PRELOAD,com.nxp.mifare,android.hardware.sensor.stepdetector,android.software.home_screen,android.hardware.context_hub,android.hardware.microphone,android.software.autofill,org.lineageos.hardware,org.lineageos.globalactions,android.software.securely_removes_users,com.google.android.feature.PIXEL_EXPERIENCE,android.hardware.bluetooth_le,android.hardware.sensor.compass,com.google.android.feature.GOOGLE_FI_BUNDLED,android.hardware.touchscreen.multitouch.jazzhand,android.hardware.sensor.barometer,android.software.app_widgets,android.hardware.telephony.carrierlock,android.software.input_methods,android.hardware.sensor.light,android.hardware.vulkan.version,android.software.companion_device_setup,android.software.device_admin,com.google.android.feature.WELLBEING,android.hardware.wifi.passpoint,android.hardware.camera,org.lineageos.trust,android.hardware.device_unique_attestation,android.hardware.screen.landscape,android.software.device_id_attestation,com.google.android.feature.AER_OPTIMIZED,android.hardware.ram.normal,org.lineageos.android,com.google.android.feature.PIXEL_2019_MIDYEAR_EXPERIENCE,android.software.managed_users,android.software.webview,android.hardware.sensor.stepcounter,android.hardware.camera.capability.manual_post_processing,android.hardware.camera.any,android.hardware.camera.capability.raw,android.hardware.vulkan.compute,android.software.connectionservice,android.hardware.touchscreen.multitouch.distinct,android.hardware.location.network,android.software.cts,android.software.sip,android.hardware.camera.capability.manual_sensor,android.software.app_enumeration,android.hardware.camera.level.full,android.hardware.identity_credential,android.hardware.wifi.direct,android.software.live_wallpaper,android.software.ipsec_tunnels,org.lineageos.settings,android.hardware.sensor.assist,android.hardware.audio.pro,android.hardware.nfc.hcef,android.hardware.nfc.uicc,android.hardware.location.gps,android.sofware.nfc.beam,android.software.midi,android.hardware.nfc.any,android.hardware.nfc.ese,android.hardware.nfc.hce,android.hardware.wifi,android.hardware.location,android.hardware.vulkan.level,android.hardware.wifi.aware,android.software.secure_lock_screen,android.hardware.biometrics.face,android.hardware.telephony,android.software.file_based_encryption")
    setProperty("Locales", "af,af_ZA,am,am_ET,ar,ar_EG,ar_XB,as,ast_ES,az,be,bg,bg_BG,bn,bs,ca,ca_ES,cs,cs_CZ,cy,da,da_DK,de,de_DE,el,el_GR,en,en_AU,en_CA,en_GB,en_IN,en_US,en_XA,en_XC,es,es_419,es_ES,es_US,et,eu,fa,fa_IR,fi,fi_FI,fil,fil_PH,fr,fr_CA,fr_FR,gd,gl,gu,hi,hi_IN,hr,hr_HR,hu,hu_HU,hy,in,in_ID,is,it,it_IT,iw,iw_IL,ja,ja_JP,ka,kk,km,kn,ko,ko_KR,ky,lo,lt,lt_LT,lv,lv_LV,mk,ml,mn,mr,ms,ms_MY,my,nb,nb_NO,ne,nl,nl_NL,or,pa,pl,pl_PL,pt,pt_BR,pt_PT,ro,ro_RO,ru,ru_RU,si,sk,sk_SK,sl,sl_SI,sq,sr,sr_Latn,sr_RS,sv,sv_SE,sw,sw_TZ,ta,te,th,th_TH,tr,tr_TR,uk,uk_UA,ur,uz,vi,vi_VN,zh_CN,zh_HK,zh_TW,zu,zu_ZA")
    setProperty("SharedLibraries", "android.test.base,android.test.mock,com.vzw.apnlib,android.hidl.manager-V1.0-java,qti-telephony-hidl-wrapper,libfastcvopt.so,google-ril,qti-telephony-utils,com.android.omadm.radioconfig,libcdsprpc.so,android.hidl.base-V1.0-java,com.qualcomm.qmapbridge,libairbrush-pixel.so,com.google.android.camera.experimental2019,libOpenCL-pixel.so,libadsprpc.so,com.android.location.provider,android.net.ipsec.ike,com.android.future.usb.accessory,libsdsprpc.so,android.ext.shared,javax.obex,izat.xt.srv,com.google.android.gms,lib_aion_buffer.so,com.qualcomm.uimremoteclientlibrary,libqdMetaData.so,com.qualcomm.uimremoteserverlibrary,com.qualcomm.qcrilhook,android.test.runner,org.apache.http.legacy,com.google.android.camera.extensions,com.google.android.hardwareinfo,com.android.cts.ctsshim.shared_library,com.android.nfc_extras,com.android.media.remotedisplay,com.android.mediadrm.signer,com.qualcomm.qti.imscmservice-V2.0-java,qti-telephony-hidl-wrapper-prd,com.qualcomm.qti.imscmservice-V2.1-java,com.qualcomm.qti.imscmservice-V2.2-java")
    setProperty("GL.Version", "196610")
    setProperty("GL.Extensions", ",GL_AMD_compressed_ATC_texture,GL_AMD_performance_monitor,GL_ANDROID_extension_pack_es31a,GL_APPLE_texture_2D_limited_npot,GL_ARB_vertex_buffer_object,GL_ARM_shader_framebuffer_fetch_depth_stencil,GL_EXT_EGL_image_array,GL_EXT_EGL_image_external_wrap_modes,GL_EXT_EGL_image_storage,GL_EXT_YUV_target,GL_EXT_blend_func_extended,GL_EXT_blit_framebuffer_params,GL_EXT_buffer_storage,GL_EXT_clip_control,GL_EXT_clip_cull_distance,GL_EXT_color_buffer_float,GL_EXT_color_buffer_half_float,GL_EXT_copy_image,GL_EXT_debug_label,GL_EXT_debug_marker,GL_EXT_discard_framebuffer,GL_EXT_disjoint_timer_query,GL_EXT_draw_buffers_indexed,GL_EXT_external_buffer,GL_EXT_fragment_invocation_density,GL_EXT_geometry_shader,GL_EXT_gpu_shader5,GL_EXT_memory_object,GL_EXT_memory_object_fd,GL_EXT_multisampled_render_to_texture,GL_EXT_multisampled_render_to_texture2,GL_EXT_primitive_bounding_box,GL_EXT_protected_textures,GL_EXT_read_format_bgra,GL_EXT_robustness,GL_EXT_sRGB,GL_EXT_sRGB_write_control,GL_EXT_shader_framebuffer_fetch,GL_EXT_shader_io_blocks,GL_EXT_shader_non_constant_global_initializers,GL_EXT_tessellation_shader,GL_EXT_texture_border_clamp,GL_EXT_texture_buffer,GL_EXT_texture_cube_map_array,GL_EXT_texture_filter_anisotropic,GL_EXT_texture_format_BGRA8888,GL_EXT_texture_format_sRGB_override,GL_EXT_texture_norm16,GL_EXT_texture_sRGB_R8,GL_EXT_texture_sRGB_decode,GL_EXT_texture_type_2_10_10_10_REV,GL_KHR_blend_equation_advanced,GL_KHR_blend_equation_advanced_coherent,GL_KHR_debug,GL_KHR_no_error,GL_KHR_robust_buffer_access_behavior,GL_KHR_texture_compression_astc_hdr,GL_KHR_texture_compression_astc_ldr,GL_NV_shader_noperspective_interpolation,GL_OES_EGL_image,GL_OES_EGL_image_external,GL_OES_EGL_image_external_essl3,GL_OES_EGL_sync,GL_OES_blend_equation_separate,GL_OES_blend_func_separate,GL_OES_blend_subtract,GL_OES_compressed_ETC1_RGB8_texture,GL_OES_compressed_paletted_texture,GL_OES_depth24,GL_OES_depth_texture,GL_OES_depth_texture_cube_map,GL_OES_draw_texture,GL_OES_element_index_uint,GL_OES_framebuffer_object,GL_OES_get_program_binary,GL_OES_matrix_palette,GL_OES_packed_depth_stencil,GL_OES_point_size_array,GL_OES_point_sprite,GL_OES_read_format,GL_OES_rgb8_rgba8,GL_OES_sample_shading,GL_OES_sample_variables,GL_OES_shader_image_atomic,GL_OES_shader_multisample_interpolation,GL_OES_standard_derivatives,GL_OES_stencil_wrap,GL_OES_surfaceless_context,GL_OES_texture_3D,GL_OES_texture_compression_astc,GL_OES_texture_cube_map,GL_OES_texture_env_crossbar,GL_OES_texture_float,GL_OES_texture_float_linear,GL_OES_texture_half_float,GL_OES_texture_half_float_linear,GL_OES_texture_mirrored_repeat,GL_OES_texture_npot,GL_OES_texture_stencil8,GL_OES_texture_storage_multisample_2d_array,GL_OES_texture_view,GL_OES_vertex_array_object,GL_OES_vertex_half_float,GL_OVR_multiview,GL_OVR_multiview2,GL_OVR_multiview_multisampled_render_to_texture,GL_QCOM_YUV_texture_gather,GL_QCOM_alpha_test,GL_QCOM_extended_get,GL_QCOM_motion_estimation,GL_QCOM_shader_framebuffer_fetch_noncoherent,GL_QCOM_shader_framebuffer_fetch_rate,GL_QCOM_texture_foveated,GL_QCOM_texture_foveated_subsampled_layout,GL_QCOM_tiled_rendering,GL_QCOM_validate_shader_binary")
    setProperty("Client", "android-google")
    setProperty("GSF.version", "223616055")
    setProperty("Vending.version", "82151710")
    setProperty("Vending.versionString", "21.5.17-21 [0] [PR] 326734551")
    setProperty("Roaming", "mobile-notroaming")
    setProperty("TimeZone", "UTC-10")
    setProperty("CellOperator", "310")
    setProperty("SimOperator", "38")
}

