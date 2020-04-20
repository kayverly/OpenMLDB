//
// count_down_latch.h
// Copyright (C) 2017 4paradigm.com
// Author vagrant
// Date 2017-09-21
//

#ifndef SRC_BASE_COUNT_DOWN_LATCH_H_
#define SRC_BASE_COUNT_DOWN_LATCH_H_
#include <condition_variable> // NOLINT
#include <mutex> // NOLINT

namespace rtidb {
namespace base {

class CountDownLatch {
 public:
    explicit CountDownLatch(int32_t count) : count_(count), mu_(), cv_() {}
    ~CountDownLatch() {}

    void CountDown() {
        std::lock_guard<std::mutex> lock(mu_);
        if (--count_ <= 0) {
            cv_.notify_all();
        }
    }

    void TimeWait(uint64_t timeout) {
        std::unique_lock<std::mutex> lock(mu_);
        cv_.wait_for(lock, std::chrono::milliseconds(timeout));
    }

    void Wait() {
        std::unique_lock<std::mutex> lock(mu_);
        while (count_ > 0) {
            cv_.wait(lock);
        }
    }

    bool IsDone() {
        std::lock_guard<std::mutex> lock(mu_);
        return count_ <= 0;
    }

    uint32_t GetCount() {
        std::lock_guard<std::mutex> lock(mu_);
        return count_;
    }

 private:
    int32_t count_;
    std::mutex mu_;
    std::condition_variable cv_;
};

}  // namespace base
}  // namespace rtidb
#endif  // SRC_BASE_COUNT_DOWN_LATCH_H_
