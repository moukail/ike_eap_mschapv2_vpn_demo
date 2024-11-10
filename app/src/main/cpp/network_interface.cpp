#include <jni.h>
#include <string>
#include <sys/types.h>
#include <ifaddrs.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <android/log.h>

#define TAG "network_interface.cpp"

// Function to get the IP address of a specific interface
std::string getIpAddress(const char *interfaceName) {
    struct ifaddrs *ifaddr, *ifa;
    char host[NI_MAXHOST];

    if (getifaddrs(&ifaddr) == -1) {
        perror("getifaddrs");
        return "";
    }
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "Interface2: %s", interfaceName);
    for (ifa = ifaddr; ifa != nullptr; ifa = ifa->ifa_next) {
        if (ifa->ifa_addr == nullptr)
            continue;



        // Check if the interface name matches
        if (strncmp(ifa->ifa_name, (const char *) interfaceName, 5) == 0) {

            // Check if it's an IPv4 address (AF_INET)
            if (ifa->ifa_addr->sa_family == AF_INET) {

                // Convert the address to a readable format
                if (getnameinfo(ifa->ifa_addr, sizeof(struct sockaddr_in), host, sizeof(host), nullptr, 0, NI_NUMERICHOST) == 0) {

                    return std::string(host);  // Return the IP address
                }
            }
        }
    }

    freeifaddrs(ifaddr);
    return "";  // Return empty string if not found
}

extern "C" JNIEXPORT jstring JNICALL
Java_nl_moukafih_ike_1vpn_1demo_MainActivity_getIpAddress(JNIEnv *env, jobject /* this */, jstring interfaceName) {
    // Convert jstring to const char*
    const char *iface = env->GetStringUTFChars(interfaceName, nullptr);
    // Log the interface name
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "Interface1: %s", iface);

    // Call your logic here to get the IP address for the given interface
    std::string ip = getIpAddress(iface);  // Assuming you have your logic in getIpAddress function

    // Release the string resources
    env->ReleaseStringUTFChars(interfaceName, iface);

    // Return the IP address as a Java string
    return env->NewStringUTF(ip.c_str());
}
