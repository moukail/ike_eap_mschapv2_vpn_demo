#include <jni.h>
#include <string>
#include <sys/types.h>
#include <ifaddrs.h>
#include <arpa/inet.h>
#include <netdb.h>

extern "C" JNIEXPORT jstring JNICALL
Java_nl_moukafih_mynative_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_nl_moukafih_ike_1vpn_1demo_MainActivity_getVpnLocalIp(JNIEnv *env, jobject) {
    std::string result;
    struct ifaddrs *ifaddr, *ifa;
    int family;

    if (getifaddrs(&ifaddr) == -1) {
        return env->NewStringUTF("Error retrieving network interfaces.");
    }

    for (ifa = ifaddr; ifa != nullptr; ifa = ifa->ifa_next) {
        if (ifa->ifa_addr == nullptr) continue;

        // Check if the interface name contains "tun" or "tap", which are common VPN interfaces
        if (std::string(ifa->ifa_name).find("tun") != std::string::npos ||
            std::string(ifa->ifa_name).find("tap") != std::string::npos ||
            std::string(ifa->ifa_name).find("ipsec") != std::string::npos) {

            family = ifa->ifa_addr->sa_family;
            if (family == AF_INET || family == AF_INET6) {
                char host[NI_MAXHOST];
                int s = getnameinfo(ifa->ifa_addr,
                                    sizeof(struct sockaddr_in),
                                    host, NI_MAXHOST, nullptr, 0, NI_NUMERICHOST);
                if (s == 0) {
                    result += std::string(host) + "\n";
                }
            }
        }
    }

    freeifaddrs(ifaddr);
    return env->NewStringUTF(result.c_str());
}