import React, { forwardRef } from 'react';
import {
  ArrowCounterClockwise as PhArrowCounterClockwise,
  ArrowLeft as PhArrowLeft,
  ArrowRight as PhArrowRight,
  ArrowsClockwise as PhArrowsClockwise,
  Bell as PhBell,
  Book as PhBook,
  BookOpen as PhBookOpen,
  Bookmarks as PhBookmarks,
  Books as PhBooks,
  CaretLeft as PhCaretLeft,
  CaretRight as PhCaretRight,
  ChartBar as PhChartBar,
  CheckCircle as PhCheckCircle,
  CircleNotch as PhCircleNotch,
  ClipboardText as PhClipboardText,
  Clock as PhClock,
  Compass as PhCompass,
  Crown as PhCrown,
  DownloadSimple as PhDownloadSimple,
  Flask as PhFlask,
  FloppyDisk as PhFloppyDisk,
  Hourglass as PhHourglass,
  Info as PhInfo,
  List as PhList,
  ListNumbers as PhListNumbers,
  MagnifyingGlass as PhMagnifyingGlass,
  MapPin as PhMapPin,
  MapTrifold as PhMapTrifold,
  Medal as PhMedal,
  Moon as PhMoon,
  Package as PhPackage,
  PencilLine as PhPencilLine,
  PencilSimple as PhPencilSimple,
  Play as PhPlay,
  Plus as PhPlus,
  Prohibit as PhProhibit,
  Question as PhQuestion,
  Receipt as PhReceipt,
  SealCheck as PhSealCheck,
  ShieldCheck as PhShieldCheck,
  SignOut as PhSignOut,
  SlidersHorizontal as PhSlidersHorizontal,
  Sparkle as PhSparkle,
  SquaresFour as PhSquaresFour,
  Star as PhStar,
  Sun as PhSun,
  Trash as PhTrash,
  Tray as PhTray,
  Trophy as PhTrophy,
  UserPlus as PhUserPlus,
  Users as PhUsers,
  Wallet as PhWallet,
  Warning as PhWarning,
  WarningCircle as PhWarningCircle,
  Wrench as PhWrench,
  X as PhX,
  XCircle as PhXCircle,
} from '@phosphor-icons/react';

/**
 * One icon language for the whole product: Phosphor's light line drawings,
 * wrapped under the old names so call sites stay readable.
 */
function icon(Component, displayName) {
  const Icon = forwardRef(function SmartLibIcon({ weight = 'light', ...props }, ref) {
    return <Component ref={ref} weight={weight} {...props} />;
  });
  Icon.displayName = displayName;
  return Icon;
}

export const AlertCircle = icon(PhWarningCircle, 'AlertCircle');
export const AlertTriangle = icon(PhWarning, 'AlertTriangle');
export const ArrowLeft = icon(PhArrowLeft, 'ArrowLeft');
export const ArrowRight = icon(PhArrowRight, 'ArrowRight');
export const BadgeCheck = icon(PhSealCheck, 'BadgeCheck');
export const Ban = icon(PhProhibit, 'Ban');
export const BarChart3 = icon(PhChartBar, 'BarChart3');
export const Bell = icon(PhBell, 'Bell');
export const BookMarked = icon(PhBookmarks, 'BookMarked');
export const BookOpen = icon(PhBookOpen, 'BookOpen');
export const BookX = icon(PhBook, 'BookX');
export const CheckCircle2 = icon(PhCheckCircle, 'CheckCircle2');
export const ChevronLeft = icon(PhCaretLeft, 'ChevronLeft');
export const ChevronRight = icon(PhCaretRight, 'ChevronRight');
export const CircleHelp = icon(PhQuestion, 'CircleHelp');
export const ClipboardList = icon(PhClipboardText, 'ClipboardList');
export const Clock3 = icon(PhClock, 'Clock3');
export const Compass = icon(PhCompass, 'Compass');
export const Crown = icon(PhCrown, 'Crown');
export const Download = icon(PhDownloadSimple, 'Download');
export const FlaskConical = icon(PhFlask, 'FlaskConical');
export const Hourglass = icon(PhHourglass, 'Hourglass');
export const Inbox = icon(PhTray, 'Inbox');
export const Info = icon(PhInfo, 'Info');
export const LayoutDashboard = icon(PhSquaresFour, 'LayoutDashboard');
export const LayoutGrid = icon(PhSquaresFour, 'LayoutGrid');
export const Library = icon(PhBooks, 'Library');
export const List = icon(PhList, 'List');
export const ListOrdered = icon(PhListNumbers, 'ListOrdered');
export const Loader2 = icon(PhCircleNotch, 'Loader2');
export const LogOut = icon(PhSignOut, 'LogOut');
export const Map = icon(PhMapTrifold, 'Map');
export const MapPin = icon(PhMapPin, 'MapPin');
export const Medal = icon(PhMedal, 'Medal');
export const Menu = icon(PhList, 'Menu');
export const Moon = icon(PhMoon, 'Moon');
export const Package = icon(PhPackage, 'Package');
export const PackageCheck = icon(PhPackage, 'PackageCheck');
export const PenLine = icon(PhPencilLine, 'PenLine');
export const Pencil = icon(PhPencilSimple, 'Pencil');
export const Play = icon(PhPlay, 'Play');
export const Plus = icon(PhPlus, 'Plus');
export const Receipt = icon(PhReceipt, 'Receipt');
export const RefreshCw = icon(PhArrowsClockwise, 'RefreshCw');
export const RotateCcw = icon(PhArrowCounterClockwise, 'RotateCcw');
export const Save = icon(PhFloppyDisk, 'Save');
export const Search = icon(PhMagnifyingGlass, 'Search');
export const ShieldCheck = icon(PhShieldCheck, 'ShieldCheck');
export const SlidersHorizontal = icon(PhSlidersHorizontal, 'SlidersHorizontal');
export const Sparkles = icon(PhSparkle, 'Sparkles');
export const Star = icon(PhStar, 'Star');
export const Sun = icon(PhSun, 'Sun');
export const Trash2 = icon(PhTrash, 'Trash2');
export const Trophy = icon(PhTrophy, 'Trophy');
export const UserPlus = icon(PhUserPlus, 'UserPlus');
export const Users = icon(PhUsers, 'Users');
export const Wallet = icon(PhWallet, 'Wallet');
export const Wrench = icon(PhWrench, 'Wrench');
export const X = icon(PhX, 'X');
export const XCircle = icon(PhXCircle, 'XCircle');