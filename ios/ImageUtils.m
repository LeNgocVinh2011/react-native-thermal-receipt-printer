//
//  ImageUtils.m
//  RNBluetoothEscposPrinter
//
//  Created by januslo on 2018/10/7.
//  Copyright © 2018年 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import "ImageUtils.h"
@implementation ImageUtils : NSObject
int p0[] = { 0, 0x80 };
int p1[] = { 0, 0x40 };
int p2[] = { 0, 0x20 };
int p3[] = { 0, 0x10 };
int p4[] = { 0, 0x08 };
int p5[] = { 0, 0x04 };
int p6[] = { 0, 0x02 };
static const uint8_t Floyd16x16[16][16] = {
    {0,128,32,160,8,136,40,168,2,130,34,162,10,138,42,170},
    {192,64,224,96,200,72,232,104,194,66,226,98,202,74,234,106},
    {48,176,16,144,56,184,24,152,50,178,18,146,58,186,26,154},
    {240,112,208,80,248,120,216,88,242,114,210,82,250,122,218,90},
    {12,140,44,172,4,132,36,164,14,142,46,174,6,134,38,166},
    {204,76,236,108,196,68,228,100,206,78,238,110,198,70,230,102},
    {60,188,28,156,52,180,20,148,62,190,30,158,54,182,22,150},
    {252,124,220,92,244,116,212,84,254,126,222,94,246,118,214,86},
    {3,131,35,163,11,139,43,171,1,129,33,161,9,137,41,169},
    {195,67,227,99,203,75,235,107,193,65,225,97,201,73,233,105},
    {51,179,19,147,59,187,27,155,49,177,17,145,57,185,25,153},
    {243,115,211,83,251,123,219,91,241,113,209,81,249,121,217,89},
    {15,143,47,175,7,135,39,167,13,141,45,173,5,133,37,165},
    {207,79,239,111,199,71,231,103,205,77,237,109,197,69,229,101},
    {63,191,31,159,55,183,23,151,61,189,29,157,53,181,21,149},
    {254,127,223,95,247,119,215,87,253,125,221,93,245,117,213,85}
};

+ (UIImage*)imagePadLeft:(NSInteger) left withSource: (UIImage*)source
{
    CGSize orgSize = [source size];
    CGSize size = CGSizeMake(orgSize.width + [[NSNumber numberWithInteger: left] floatValue], orgSize.height);
    UIGraphicsBeginImageContext(size);
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSetFillColorWithColor(context,
                                   [[UIColor whiteColor] CGColor]);
    CGContextFillRect(context, CGRectMake(0, 0, size.width, size.height));
    [source drawInRect:CGRectMake(left, 0, orgSize.width, orgSize.height)
             blendMode:kCGBlendModeNormal alpha:1.0];
    UIImage *paddedImage =  UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return paddedImage;
}

+(uint8_t *)imageToGreyImage:(UIImage *)image
{
    CGImageRef cgImage = image.CGImage;
    size_t width = CGImageGetWidth(cgImage);
    size_t height = CGImageGetHeight(cgImage);
    uint32_t *rgbImage = (uint32_t *)calloc(width * height, sizeof(uint32_t));
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    CGContextRef context =
    CGBitmapContextCreate(rgbImage,
                          width,
                          height,
                          8,
                          width * 4,
                          colorSpace,
                          kCGBitmapByteOrder32Little |
                          kCGImageAlphaNoneSkipLast);
    CGContextDrawImage(context,
                       CGRectMake(0,0,width,height),
                       cgImage);
    CGContextRelease(context);
    CGColorSpaceRelease(colorSpace);
    uint8_t *gray = malloc(width * height);
    NSInteger k = 0;
    for (NSInteger y = 0; y < height; y++) {
        for (NSInteger x = 0; x < width; x++) {
            uint32_t rgb = rgbImage[k];
            uint8_t r = (rgb >> 24) & 0xff;
            uint8_t g = (rgb >> 16) & 0xff;
            uint8_t b = (rgb >> 8) & 0xff;
            gray[k] =
            (uint8_t)(0.299f*r +
                      0.587f*g +
                      0.114f*b);
            k++;
        }
    }
    free(rgbImage);
    return gray;
}

+(uint8_t *)bitmapToBWPix:(UIImage *)image
{
    CGImageRef cgImage = image.CGImage;
    size_t width = CGImageGetWidth(cgImage);
    size_t height = CGImageGetHeight(cgImage);
    uint8_t *gray =
    [ImageUtils imageToGreyImage:image];
    uint8_t *bw =
    malloc(width * height);
    NSInteger k = 0;
    for (NSInteger y = 0; y < height; y++) {
        for (NSInteger x = 0; x < width; x++) {
            bw[k] =
            (gray[k] >
             Floyd16x16[x & 15][y & 15])
            ? 0
            : 1;
            k++;
        }
    }
    free(gray);
    return bw;
}

+ (UIImage *)imageWithImage:(UIImage *)image scaledToFillSize:(CGSize)size
{
    CGFloat scale = MAX(size.width/image.size.width, size.height/image.size.height);
    CGFloat width = image.size.width * scale;
    CGFloat height = image.size.height * scale;
    CGRect imageRect = CGRectMake((size.width - width)/2.0f,
                                  (size.height - height)/2.0f,
                                  width,
                                  height);
    
    UIGraphicsBeginImageContextWithOptions(size, YES, 1.0);
    [[UIColor whiteColor] setFill];
    UIRectFill(CGRectMake(0, 0, size.width, size.height));
    [image drawInRect:imageRect];
    UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return newImage;
}

+ (NSData*)bitmapToArray:(UIImage*) bmp
{
    CGDataProviderRef provider = CGImageGetDataProvider(bmp.CGImage);
    NSData* data = (id)CFBridgingRelease(CGDataProviderCopyData(provider));
    return data;
}

+ (NSData *)eachLinePixToCmd:(unsigned char *)src nWidth:(NSInteger) nWidth nHeight:(NSInteger) nHeight nMode:(NSInteger) nMode
{
    NSLog(@"SIZE OF SRC: %lu",sizeof(&src));
    NSInteger nBytesPerLine = (int)nWidth/8;
    unsigned char * data = malloc(nHeight*(8+nBytesPerLine));
    NSInteger k = 0;
    for(int i=0;i<nHeight;i++){
        NSInteger var10 = i*(8+nBytesPerLine);
                data[var10 + 0] = 29;//GS
                data[var10 + 1] = 118;//v
                data[var10 + 2] = 48;//0
                data[var10 + 3] =  (unsigned char)(nMode & 1);
                data[var10 + 4] =  (unsigned char)(nBytesPerLine % 256);//xL
                data[var10 + 5] =  (unsigned char)(nBytesPerLine / 256);//xH
                data[var10 + 6] = 1;//yL
                data[var10 + 7] = 0;//yH

        for (int j = 0; j < nBytesPerLine; ++j) {
            data[var10 + 8 + j] = (int) (p0[src[k]] + p1[src[k + 1]] + p2[src[k + 2]] + p3[src[k + 3]] + p4[src[k + 4]] + p5[src[k + 5]] + p6[src[k + 6]] + src[k + 7]);
            k =k+8;
        }
    }
    return [NSData dataWithBytes:data length:nHeight*(8+nBytesPerLine)];
}

+(unsigned char *)format_K_threshold:(unsigned char *) orgpixels
                        width:(NSInteger) xsize height:(NSInteger) ysize
{
    unsigned char * despixels = malloc(xsize*ysize);
    int graytotal = 0;
    int k = 0;
    
    int i;
    int j;
    int gray;
    for(i = 0; i < ysize; ++i) {
        for(j = 0; j < xsize; ++j) {
            gray = orgpixels[k] & 255;
            graytotal += gray;
            ++k;
        }
    }
    
    int grayave = graytotal / ysize / xsize;
    k = 0;
    for(i = 0; i < ysize; ++i) {
        for(j = 0; j < xsize; ++j) {
            gray = orgpixels[k] & 255;
            if(gray > grayave) {
                despixels[k] = 0;
            } else {
                despixels[k] = 1;
               // oneCount++;
            }
            
            ++k;
        }
    }
    return despixels;
}

+(NSData *)pixToTscCmd:(uint8_t *)src width:(NSInteger)width
{
    NSInteger length = width / 8;
    uint8_t *data = malloc(length);
    NSInteger j = 0;
    for (NSInteger k = 0; k < length; k++) {
        uint8_t temp =
        (uint8_t)(
                  p0[src[j]]
                  + p1[src[j+1]]
                  + p2[src[j+2]]
                  + p3[src[j+3]]
                  + p4[src[j+4]]
                  + p5[src[j+5]]
                  + p6[src[j+6]]
                  + src[j+7]);
        data[k] = (uint8_t)(~temp);
        j += 8;
    }
    NSData *result =
    [NSData dataWithBytes:data
                   length:length];
    free(data);
    return result;
}

@end
