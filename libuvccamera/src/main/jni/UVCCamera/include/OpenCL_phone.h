#ifndef  OPENCL_PHONE_H
#define OPENCL_PHONE_H

#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include<CL/cl.h>

#define ERROR_NO    0  /*OpenCL没有问题*/
#define ERROR_LIBRARY   -10  /*没有动态库*/
#define ERROR_FUN_INIT   -11  /*动态库函数错误*/
#define ERROR_FUN_CALL  -12 /*OpenCL函数执行错误*/


  extern cl_int (*rclGetPlatformIDs)(
		cl_uint          /* num_entries */,
		cl_platform_id * /* platforms */,
		cl_uint *        /* num_platforms */);

  extern cl_int (*rclGetPlatformInfo)(
		cl_platform_id   /* platform */,
		cl_platform_info /* param_name */,
		size_t           /* param_value_size */,
		void *           /* param_value */,
		size_t *         /* param_value_size_ret */);

  extern cl_int (*rclGetDeviceIDs)(
		cl_platform_id   /* platform */,
		cl_device_type   /* device_type */,
		cl_uint          /* num_entries */,
		cl_device_id *   /* devices */,
		cl_uint *        /* num_devices */);


  extern cl_int (*rclGetDeviceInfo)(
		cl_device_id    /* device */,
		cl_device_info  /* param_name */,
		size_t          /* param_value_size */,
		void *          /* param_value */,
		size_t *        /* param_value_size_ret */);

  extern cl_context (*rclCreateContext)(
		const cl_context_properties * /* properties */,
        cl_uint                 /* num_devices */,
        const cl_device_id *    /* devices */,
        void (CL_CALLBACK * /* pfn_notify */)(const char *, const void *, size_t, void *),
        void *                  /* user_data */,
        cl_int *                /* errcode_ret */);

  extern cl_int (*rclReleaseContext)(
		  cl_context /* context */);

  extern cl_command_queue (*rclCreateCommandQueue)(
		cl_context                     /* context */,
		cl_device_id                   /* device */,
		cl_command_queue_properties    /* properties */,
		cl_int *                       /* errcode_ret */);

  extern cl_int (*rclReleaseCommandQueue)(
		  cl_command_queue /* command_queue */) ;

  extern cl_program (*rclCreateProgramWithSource)(
		cl_context        /* context */,
		cl_uint           /* count */,
		const char **     /* strings */,
		const size_t *    /* lengths */,
		cl_int *          /* errcode_ret */) ;

  extern cl_int(*rclReleaseProgram)(
		cl_program /* program */);

  extern cl_int (*rclBuildProgram)(
		cl_program           /* program */,
		cl_uint              /* num_devices */,
		const cl_device_id * /* device_list */,
		const char *         /* options */,
		void (CL_CALLBACK *  /* pfn_notify */)(cl_program /* program */, void * /* user_data */),
		void *               /* user_data */) ;

  extern cl_int(*rclGetProgramBuildInfo)(
		cl_program            /* program */,
        cl_device_id          /* device */,
        cl_program_build_info /* param_name */,
        size_t                /* param_value_size */,
        void *                /* param_value */,
        size_t *              /* param_value_size_ret */);

  extern cl_int (*rclGetContextInfo)(
		cl_context         /* context */,
		cl_context_info    /* param_name */,
		size_t             /* param_value_size */,
        void *             /* param_value */,
        size_t *           /* param_value_size_ret */);

  extern cl_int (*rclGetEventProfilingInfo)(
		cl_event            /* event */,
		cl_profiling_info   /* param_name */,
		size_t              /* param_value_size */,
		void *              /* param_value */,
		size_t *			/* param_value_size_ret */ );
/*load library*/

  extern cl_mem (*rclCreateBuffer)(
		cl_context   /* context */,
		cl_mem_flags /* flags */,
		size_t       /* size */,
		void *       /* host_ptr */,
		cl_int *     /* errcode_ret */);
  extern cl_int(*rclReleaseMemObject)(
		  cl_mem /* memobj */);

  extern cl_kernel	(*rclCreateKernel)(
		cl_program      /* program */,
		const char *    /* kernel_name */,
		cl_int *        /* errcode_ret */) ;

  extern cl_int(*rclReleaseKernel)(
		  cl_kernel   /* kernel */) ;

  extern cl_int (*rclSetKernelArg)(
		cl_kernel    /* kernel */,
		cl_uint      /* arg_index */,
		size_t       /* arg_size */,
		const void * /* arg_value */);

  extern cl_int(*rclEnqueueNDRangeKernel)(
		cl_command_queue /* command_queue */,
		cl_kernel        /* kernel */,
		cl_uint          /* work_dim */,
		const size_t *   /* global_work_offset */,
		const size_t *   /* global_work_size */,
		const size_t *   /* local_work_size */,
		cl_uint          /* num_events_in_wait_list */,
		const cl_event * /* event_wait_list */,
		cl_event *       /* event */);

  /* Flush and Finish APIs */
  extern cl_int(*rclFlush)(
		  cl_command_queue /* command_queue */) ;
  extern cl_int	(*rclFinish)(
		cl_command_queue /* command_queue */);

  extern cl_int (*rclEnqueueReadBuffer)(
		cl_command_queue    /* command_queue */,
		cl_mem              /* buffer */,
		cl_bool             /* blocking_read */,
		size_t              /* offset */,
		size_t              /* size */,
		void *              /* ptr */,
		cl_uint             /* num_events_in_wait_list */,
		const cl_event *    /* event_wait_list */,
		cl_event *          /* event */);

  extern cl_int (*rclEnqueueWriteBuffer)(
		cl_command_queue   /* command_queue */,
		cl_mem             /* buffer */,
		cl_bool            /* blocking_write */,
		size_t             /* offset */,
		size_t             /* size */,
		const void *       /* ptr */,
		cl_uint            /* num_events_in_wait_list */,
		const cl_event *   /* event_wait_list */,
		cl_event *         /* event */);


  extern cl_int(*rclEnqueueCopyBuffer)(
		  cl_command_queue    /* command_queue */,
		  cl_mem              /* src_buffer */,
		  cl_mem              /* dst_buffer */,
		  size_t              /* src_offset */,
		  size_t              /* dst_offset */,
		  size_t              /* size */,
		  cl_uint             /* num_events_in_wait_list */,
		  const cl_event *    /* event_wait_list */,
		  cl_event *          /* event */) ;

  extern cl_int(* rclGetKernelInfo)(
		 cl_kernel       /* kernel */,
         cl_kernel_info  /* param_name */,
         size_t          /* param_value_size */,
         void *          /* param_value */,
         size_t *        /* param_value_size_ret */) ;

  extern cl_int(*rclGetKernelWorkGroupInfo)(
		  cl_kernel                  /* kernel */,
		  cl_device_id               /* device */,
		  cl_kernel_work_group_info  /* param_name */,
		  size_t                     /* param_value_size */,
		  void *                     /* param_value */,
		  size_t *                   /* param_value_size_ret */) ;


  /* Event Object APIs */
  extern cl_int(*rclWaitForEvents)(
		  cl_uint             /* num_events */,
          const cl_event *    /* event_list */) ;

  extern cl_int(*rclGetEventInfo)(
		  cl_event         /* event */,
		  cl_event_info    /* param_name */,
		  size_t           /* param_value_size */,
		  void *           /* param_value */,
		  size_t *         /* param_value_size_ret */) ;

  extern cl_event(*rclCreateUserEvent)(
		  cl_context    /* context */,
          cl_int *      /* errcode_ret */) ;

  extern cl_int(*rclRetainEvent)(
		  cl_event /* event */) ;

  extern cl_int(*rclReleaseEvent)(
		  cl_event /* event */) ;

  extern cl_int (* rclSetUserEventStatus)(
		  cl_event   /* event */,
          cl_int     /* execution_status */) ;

  extern cl_int(*rclSetEventCallback)(
		  cl_event    /* event */,
          cl_int      /* command_exec_callback_type */,
           void (CL_CALLBACK * /* pfn_notify */)(cl_event, cl_int, void *),
           void *      /* user_data */) ;

  extern  void * CL_API_CALL(*rclEnqueueMapBuffer)(
		  cl_command_queue /* command_queue */,
          cl_mem           /* buffer */,
          cl_bool          /* blocking_map */,
          cl_map_flags     /* map_flags */,
          size_t           /* offset */,
          size_t           /* size */,
          cl_uint          /* num_events_in_wait_list */,
          const cl_event * /* event_wait_list */,
          cl_event *       /* event */,
          cl_int *         /* errcode_ret */) ;

  extern cl_int (*rclEnqueueUnmapMemObject)(
		  cl_command_queue /* command_queue */,
		  cl_mem           /* memobj */,
		  void *           /* mapped_ptr */,
		  cl_uint          /* num_events_in_wait_list */,
		  const cl_event *  /* event_wait_list */,
		  cl_event *        /* event */) ;
void *getCLHandle(int currentAndroidVersion);
int load_Func(int currentAndroidVersion);


#endif
