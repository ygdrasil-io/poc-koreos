// Minimal Win32 API declarations for kextract --win32 mode.
// Overcomes the need for a full Windows SDK installation.
// Types follow Win64 convention (LLP64).
typedef int                 BOOL;
typedef unsigned char       BYTE;
typedef unsigned short      WORD;
typedef unsigned long       DWORD;
typedef int                 INT;
typedef long                LONG;
typedef long long           LONGLONG;
typedef long long           LONG_PTR;
typedef unsigned long long  ULONG_PTR;
typedef unsigned int        UINT;
typedef short               SHORT;
typedef unsigned short      ATOM;

typedef void*               HANDLE;
typedef HANDLE              HWND;
typedef HANDLE              HINSTANCE;
typedef HANDLE              HMENU;
typedef HANDLE              HICON;
typedef HANDLE              HCURSOR;
typedef HANDLE              HGDIOBJ;
typedef HANDLE              HRGN;
typedef HANDLE              HDROP;
typedef HANDLE              HTOUCHINPUT;
typedef HANDLE              HGESTUREINFO;
typedef HANDLE              HMODULE;
typedef long                HRESULT;
typedef void*               LPVOID;
typedef const void*         LPCVOID;
typedef const unsigned short* LPCWSTR;
typedef unsigned short*     LPWSTR;
typedef void*               LPMSG;
typedef void*               LPPOINT;
typedef void*               LPRECT;
typedef void*               LPTRACKMOUSEEVENT;
typedef void*               PTOUCHINPUT;
typedef void*               PGESTUREINFO;
typedef void*               LPINPUT;
typedef void*               LRESULT;
typedef void*               WPARAM;
typedef void*               LPARAM;

// === User32.dll ===

ATOM    RegisterClassExW(const void*);
HWND    CreateWindowExW(DWORD, LPCWSTR, LPCWSTR, DWORD, INT, INT, INT, INT, HWND, HMENU, HINSTANCE, LPVOID);
BOOL    ShowWindow(HWND, INT);
BOOL    UpdateWindow(HWND);
BOOL    DestroyWindow(HWND);
LRESULT DefWindowProcW(HWND, UINT, WPARAM, LPARAM);
BOOL    SetWindowTextW(HWND, LPCWSTR);
void    PostQuitMessage(INT);
SHORT   GetKeyState(INT);
BOOL    PeekMessageW(LPMSG, HWND, UINT, UINT, UINT);
BOOL    GetMessageW(LPMSG, HWND, UINT, UINT);
BOOL    TranslateMessage(const void*);
LRESULT DispatchMessageW(const void*);
DWORD   MsgWaitForMultipleObjectsEx(DWORD, const HANDLE*, DWORD, DWORD, DWORD);
HCURSOR LoadCursorW(HINSTANCE, LPCWSTR);
BOOL    SetProcessDpiAwarenessContext(HANDLE);
UINT    GetDpiForWindow(HWND);
BOOL    TrackMouseEvent(LPTRACKMOUSEEVENT);
BOOL    RegisterTouchWindow(HWND, ULONG_PTR);
BOOL    GetTouchInputInfo(HTOUCHINPUT, UINT, PTOUCHINPUT, INT);
BOOL    CloseTouchInputHandle(HTOUCHINPUT);
BOOL    GetGestureInfo(HGESTUREINFO, PGESTUREINFO);
BOOL    CloseGestureInfoHandle(HGESTUREINFO);
BOOL    ScreenToClient(HWND, LPPOINT);
BOOL    ClientToScreen(HWND, LPPOINT);
BOOL    GetCursorPos(LPPOINT);
HMENU   GetSystemMenu(HWND, BOOL);
BOOL    TrackPopupMenu(HMENU, UINT, INT, INT, INT, HWND, const void*);
BOOL    EnableMenuItem(HMENU, UINT, UINT);
BOOL    SetMenuDefaultItem(HMENU, UINT, UINT);
BOOL    ReleaseCapture(void);
BOOL    EnableWindow(HWND, BOOL);
BOOL    GetClientRect(HWND, LPRECT);
BOOL    GetWindowRect(HWND, LPRECT);
LONG_PTR SetWindowLongPtrW(HWND, INT, LONG_PTR);
LONG_PTR GetWindowLongPtrW(HWND, INT);
BOOL    IsZoomed(HWND);
BOOL    IsIconic(HWND);
BOOL    IsWindowVisible(HWND);
INT     GetWindowTextW(HWND, LPWSTR, INT);
BOOL    SetWindowPos(HWND, HWND, INT, INT, INT, INT, UINT);
HWND    GetForegroundWindow(void);
BOOL    SetForegroundWindow(HWND);
UINT    MapVirtualKeyW(UINT, UINT);
UINT    SendInput(UINT, LPINPUT, INT);
BOOL    SetCursorPos(INT, INT);
HCURSOR SetCursor(HCURSOR);
INT     ShowCursor(BOOL);
BOOL    ClipCursor(const void*);
LRESULT SendMessageW(HWND, UINT, WPARAM, LPARAM);
BOOL    PostMessageW(HWND, UINT, WPARAM, LPARAM);
HICON   CreateIcon(HINSTANCE, INT, INT, BYTE, BYTE, const BYTE*, const BYTE*);
BOOL    DestroyIcon(HICON);

// === Kernel32.dll ===

DWORD   GetCurrentThreadId(void);
HMODULE GetModuleHandleW(LPCWSTR);
void    SetLastError(DWORD);
DWORD   GetLastError(void);

// === Gdi32.dll ===

HRGN    CreateRectRgn(INT, INT, INT, INT);
BOOL    DeleteObject(HGDIOBJ);

// === Dwmapi.dll ===

HRESULT DwmSetWindowAttribute(HWND, DWORD, LPCVOID, DWORD);
HRESULT DwmEnableBlurBehindWindow(HWND, const void*);
HRESULT DwmExtendFrameIntoClientArea(HWND, const void*);
